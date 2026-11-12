import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowLeftRight,
  CalendarClock,
  ChartPie,
  CreditCard,
  FileText,
  HandCoins,
  LayoutGrid,
  LifeBuoy,
  ListOrdered,
  PiggyBank,
  Search,
  Settings,
  Shield,
  Snowflake,
  Users,
  Wallet,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { transfersApi } from '../api/transfers';
import { beneficiariesApi } from '../api/beneficiaries';
import { cardsApi } from '../api/cards';
import type { BeneficiaryResponse, CardResponse, TransactionResponse } from '../api/types';
import { formatMoney, formatDate, maskAccountNumber } from '../utils/format';
import { categoryMeta } from '../utils/categories';

/** How long to wait after the last keystroke before asking the server. */
const SEARCH_DEBOUNCE_MS = 220;

/**
 * Opens the palette from anywhere.
 *
 * A custom event rather than a context, because the two call sites — the search
 * button in the desktop bar and the icon on a phone — are in the layout, while
 * the palette itself sits at the application root. Threading a setter down
 * through both would be more plumbing than the feature is worth.
 */
export function openCommandPalette() {
  window.dispatchEvent(new CustomEvent('mbank:command-palette'));
}

interface Command {
  id: string;
  label: string;
  hint?: string;
  icon: ReactNode;
  group: string;
  run: () => void;
  /** Extra words the entry should match on, beyond its label. */
  keywords?: string;
}

/**
 * Search and quick actions, on ⌘K.
 *
 * Two things at once, deliberately. Somebody who types "netflix" wants their
 * transactions; somebody who types "freeze" wants an action. Splitting them
 * across two surfaces would mean guessing which one to open before knowing what
 * you were looking for.
 *
 * Navigation is filtered locally and instantly. Transactions, payees and cards
 * come from the server, debounced, and only once there is enough of a query to
 * be worth a request.
 */
export function CommandPalette() {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [cursor, setCursor] = useState(0);
  const [transactions, setTransactions] = useState<TransactionResponse[]>([]);
  const [payees, setPayees] = useState<BeneficiaryResponse[]>([]);
  const [cards, setCards] = useState<CardResponse[]>([]);
  const [searching, setSearching] = useState(false);
  const inputRef = useRef<HTMLInputElement | null>(null);

  const close = useCallback(() => {
    setOpen(false);
    setQuery('');
    setTransactions([]);
    setPayees([]);
    setCards([]);
    setCursor(0);
  }, []);

  const go = useCallback(
    (to: string) => {
      close();
      navigate(to);
    },
    [close, navigate],
  );

  // --- opening ---------------------------------------------------------------
  useEffect(() => {
    const onShortcut = (event: KeyboardEvent) => {
      // metaKey on a Mac, ctrlKey everywhere else. Both are accepted on both,
      // because muscle memory does not respect platform boundaries.
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setOpen((current) => !current);
      }
    };
    const onRequest = () => setOpen(true);

    window.addEventListener('keydown', onShortcut);
    window.addEventListener('mbank:command-palette', onRequest);
    return () => {
      window.removeEventListener('keydown', onShortcut);
      window.removeEventListener('mbank:command-palette', onRequest);
    };
  }, []);

  useEffect(() => {
    if (open) {
      // Focus on the next frame: the input does not exist until this render
      // commits.
      const raf = requestAnimationFrame(() => inputRef.current?.focus());
      return () => cancelAnimationFrame(raf);
    }
    return undefined;
  }, [open]);

  // --- server-side search ----------------------------------------------------
  useEffect(() => {
    const trimmed = query.trim();
    // One or two characters match nearly everything, so the request would cost a
    // round trip to return noise.
    if (!open || trimmed.length < 2) {
      setTransactions([]);
      setPayees([]);
      setCards([]);
      return undefined;
    }

    let cancelled = false;
    setSearching(true);
    const timer = window.setTimeout(async () => {
      try {
        const [transactionPage, payeeList, cardList] = await Promise.all([
          transfersApi.listTransactions({ q: trimmed, size: 5 }),
          beneficiariesApi.list(),
          cardsApi.list(),
        ]);
        if (cancelled) return;
        setTransactions(transactionPage.content);
        const needle = trimmed.toLowerCase();
        setPayees(
          payeeList
            .filter(
              (payee) =>
                payee.nickname.toLowerCase().includes(needle) ||
                payee.accountNumber.toLowerCase().includes(needle),
            )
            .slice(0, 4),
        );
        setCards(
          cardList
            .filter(
              (card) => card.label.toLowerCase().includes(needle) || card.lastFour.includes(needle),
            )
            .slice(0, 4),
        );
      } catch {
        // A failed search shows no results rather than an error dialog: the
        // customer is mid-keystroke and the next one will try again.
        if (!cancelled) {
          setTransactions([]);
          setPayees([]);
          setCards([]);
        }
      } finally {
        if (!cancelled) setSearching(false);
      }
    }, SEARCH_DEBOUNCE_MS);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [query, open]);

  // --- the fixed commands ----------------------------------------------------
  const commands = useMemo<Command[]>(
    () => [
      { id: 'send', label: 'Send money', icon: <ArrowLeftRight />, group: 'Actions', run: () => go('/payments/send'), keywords: 'transfer pay' },
      { id: 'request', label: 'Request money', icon: <HandCoins />, group: 'Actions', run: () => go('/payments/requests'), keywords: 'ask invoice' },
      { id: 'new-card', label: 'Create a card', icon: <CreditCard />, group: 'Actions', run: () => go('/cards'), keywords: 'issue virtual' },
      { id: 'freeze', label: 'Freeze a card', icon: <Snowflake />, group: 'Actions', run: () => go('/cards'), keywords: 'lock block stop' },
      { id: 'goal', label: 'Start a savings goal', icon: <PiggyBank />, group: 'Actions', run: () => go('/savings'), keywords: 'save target' },
      { id: 'statement', label: 'Download a statement', icon: <FileText />, group: 'Actions', run: () => go('/statements'), keywords: 'export csv pdf' },

      { id: 'overview', label: 'Home', icon: <LayoutGrid />, group: 'Go to', run: () => go('/'), keywords: 'home dashboard' },
      { id: 'accounts', label: 'Accounts', icon: <Wallet />, group: 'Go to', run: () => go('/accounts'), keywords: 'balance iban' },
      { id: 'cards', label: 'Cards', icon: <CreditCard />, group: 'Go to', run: () => go('/cards') },
      { id: 'transactions', label: 'Transactions', icon: <ListOrdered />, group: 'Go to', run: () => go('/transactions'), keywords: 'history activity' },
      { id: 'analytics', label: 'Analytics', icon: <ChartPie />, group: 'Go to', run: () => go('/analytics'), keywords: 'spending insights budget' },
      { id: 'savings', label: 'Savings', icon: <PiggyBank />, group: 'Go to', run: () => go('/savings') },
      { id: 'scheduled', label: 'Scheduled payments', icon: <CalendarClock />, group: 'Go to', run: () => go('/payments/scheduled'), keywords: 'recurring standing order' },
      { id: 'payees', label: 'Payees', icon: <Users />, group: 'Go to', run: () => go('/payments/payees'), keywords: 'beneficiaries contacts' },
      { id: 'security', label: 'Security', icon: <Shield />, group: 'Go to', run: () => go('/security'), keywords: 'sessions devices password' },
      { id: 'settings', label: 'Settings', icon: <Settings />, group: 'Go to', run: () => go('/profile'), keywords: 'preferences theme' },
      { id: 'support', label: 'Support', icon: <LifeBuoy />, group: 'Go to', run: () => go('/support'), keywords: 'help contact problem' },
    ],
    [go],
  );

  // --- everything currently selectable, in the order it is drawn -------------
  const results = useMemo<Command[]>(() => {
    const needle = query.trim().toLowerCase();

    const matching = needle
      ? commands.filter(
          (command) =>
            command.label.toLowerCase().includes(needle) ||
            (command.keywords ?? '').includes(needle),
        )
      : commands;

    const found: Command[] = transactions.map((transaction) => ({
      id: `t-${transaction.transferId}`,
      label: transaction.description || categoryMeta(transaction.category).label,
      hint: `${formatMoney(transaction.amount, transaction.currency)} · ${formatDate(transaction.createdAt)}`,
      icon: <ListOrdered />,
      group: 'Transactions',
      run: () => go(`/transactions?q=${encodeURIComponent(query.trim())}`),
    }));

    const foundPayees: Command[] = payees.map((payee) => ({
      id: `b-${payee.id}`,
      label: payee.nickname,
      hint: maskAccountNumber(payee.accountNumber),
      icon: <Users />,
      group: 'Payees',
      run: () => go(`/payments/send?to=${encodeURIComponent(payee.accountNumber)}`),
    }));

    const foundCards: Command[] = cards.map((card) => ({
      id: `c-${card.id}`,
      label: card.label,
      hint: `•••• ${card.lastFour}`,
      icon: <CreditCard />,
      group: 'Cards',
      run: () => go('/cards'),
    }));

    // Actions first: somebody who opened the palette to do something should not
    // have to scroll past their own transaction history to reach it.
    return [...matching, ...found, ...foundPayees, ...foundCards];
  }, [commands, query, transactions, payees, cards, go]);

  // The cursor must not point past the end when the results shrink under it.
  useEffect(() => {
    setCursor((current) => Math.min(current, Math.max(0, results.length - 1)));
  }, [results.length]);

  if (!open) return null;

  const onKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === 'Escape') {
      close();
    } else if (event.key === 'ArrowDown') {
      event.preventDefault();
      setCursor((current) => (current + 1) % Math.max(1, results.length));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setCursor((current) => (current - 1 + results.length) % Math.max(1, results.length));
    } else if (event.key === 'Enter') {
      event.preventDefault();
      results[cursor]?.run();
    }
  };

  // Grouped for rendering, while `results` keeps the flat order the cursor uses.
  const groups: { name: string; items: { command: Command; index: number }[] }[] = [];
  results.forEach((command, index) => {
    let group = groups.find((candidate) => candidate.name === command.group);
    if (!group) {
      group = { name: command.group, items: [] };
      groups.push(group);
    }
    group.items.push({ command, index });
  });

  return (
    <div className="palette-scrim" onClick={close}>
      <div
        className="palette surface--raised"
        role="dialog"
        aria-modal="true"
        aria-label="Search MBank"
        onClick={(event) => event.stopPropagation()}
        onKeyDown={onKeyDown}
      >
        <div className="palette__search">
          <Search aria-hidden="true" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setCursor(0);
            }}
            placeholder="Search transactions, payees, cards — or type an action"
            aria-label="Search MBank"
            autoComplete="off"
            spellCheck={false}
          />
          <kbd className="palette__esc">esc</kbd>
        </div>

        <div className="palette__results" role="listbox" aria-label="Results">
          {results.length === 0 && (
            <p className="palette__empty">
              {searching ? 'Searching…' : `Nothing matches “${query.trim()}”.`}
            </p>
          )}

          {groups.map((group) => (
            <div key={group.name} className="palette__group">
              <div className="palette__group-name">{group.name}</div>
              {group.items.map(({ command, index }) => (
                <button
                  key={command.id}
                  type="button"
                  role="option"
                  aria-selected={index === cursor}
                  className={`palette__item ${index === cursor ? 'palette__item--active' : ''}`}
                  onClick={command.run}
                  /* Hovering moves the cursor, so the pointer and the keyboard
                     never disagree about which row is selected. */
                  onMouseEnter={() => setCursor(index)}
                >
                  <span className="palette__icon">{command.icon}</span>
                  <span className="grow truncate" style={{ textAlign: 'left' }}>
                    {command.label}
                  </span>
                  {command.hint && <span className="palette__hint truncate">{command.hint}</span>}
                </button>
              ))}
            </div>
          ))}
        </div>

        <footer className="palette__foot">
          <span>
            <kbd>↑</kbd>
            <kbd>↓</kbd> to move
          </span>
          <span>
            <kbd>↵</kbd> to select
          </span>
          <span>
            <kbd>esc</kbd> to close
          </span>
        </footer>
      </div>
    </div>
  );
}
