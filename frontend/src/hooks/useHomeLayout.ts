import { useCallback, useEffect, useState } from 'react';

/**
 * The blocks a customer can put on their home screen.
 *
 * The balance and the quick actions are deliberately not on this list. They are
 * the two things every customer opens the app for, and a home screen that can be
 * configured into not showing the balance is a home screen with a bug in it.
 */
export const HOME_WIDGETS = [
  'cards',
  'recent',
  'spending',
  'goals',
  'upcoming',
  'payees',
  'accounts',
] as const;

export type HomeWidget = (typeof HOME_WIDGETS)[number];

export interface WidgetMeta {
  title: string;
  /** One line in the editor, explaining what the block shows. */
  description: string;
}

export const WIDGET_META: Record<HomeWidget, WidgetMeta> = {
  cards: { title: 'Cards', description: 'Your wallet, swipeable.' },
  recent: { title: 'Recent transactions', description: 'The last few movements on your account.' },
  spending: { title: 'Spending this month', description: 'Where the money went, by category.' },
  goals: { title: 'Savings goals', description: 'Progress towards what you are putting money aside for.' },
  upcoming: { title: 'Coming up', description: 'Scheduled payments due soon.' },
  payees: { title: 'Favourite payees', description: 'The people you send to most.' },
  accounts: { title: 'Accounts', description: 'Every currency you hold, and its balance.' },
};

/** What a new customer sees before they have touched anything. */
const DEFAULT_LAYOUT: HomeWidget[] = ['cards', 'recent', 'spending', 'goals', 'upcoming'];

const STORAGE_KEY = 'minibank.home.layout';

/**
 * Parses a stored layout, discarding anything that is no longer a widget.
 *
 * A layout saved by an older build can name a block that has since been removed.
 * Filtering rather than rejecting means one retired widget does not throw away
 * the rest of somebody's arrangement.
 */
function parse(raw: string | null): HomeWidget[] | null {
  if (!raw) return null;
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return null;
    const known = parsed.filter((id): id is HomeWidget => HOME_WIDGETS.includes(id as HomeWidget));
    // An empty result means every stored id has been retired, which is closer to
    // "no preference" than to "a home screen with nothing on it".
    return known.length > 0 ? known : null;
  } catch {
    return null;
  }
}

/**
 * Which blocks are on the home screen, and in what order.
 *
 * Held in `localStorage` rather than on the server. This is a display
 * preference, not banking state: it changes nothing about the money, it is
 * worth nothing to anybody else, and putting it behind a round trip would make
 * reordering a block feel like submitting a form. The cost is that the
 * arrangement does not follow the customer to a second device, which is the
 * right trade for a preference of this weight.
 */
export function useHomeLayout() {
  const [layout, setLayout] = useState<HomeWidget[]>(() => {
    try {
      return parse(localStorage.getItem(STORAGE_KEY)) ?? DEFAULT_LAYOUT;
    } catch {
      // Private browsing with storage denied.
      return DEFAULT_LAYOUT;
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(layout));
    } catch {
      // The arrangement still applies for this session; it will not be remembered.
    }
  }, [layout]);

  const toggle = useCallback((id: HomeWidget) => {
    setLayout((current) =>
      current.includes(id) ? current.filter((entry) => entry !== id) : [...current, id],
    );
  }, []);

  /** Moves one block up or down. Clamped, so the ends of the list are not dead. */
  const move = useCallback((id: HomeWidget, direction: -1 | 1) => {
    setLayout((current) => {
      const from = current.indexOf(id);
      if (from === -1) return current;
      const to = from + direction;
      if (to < 0 || to >= current.length) return current;
      const next = [...current];
      const [moved] = next.splice(from, 1);
      next.splice(to, 0, moved!);
      return next;
    });
  }, []);

  const reset = useCallback(() => setLayout(DEFAULT_LAYOUT), []);

  return { layout, toggle, move, reset };
}
