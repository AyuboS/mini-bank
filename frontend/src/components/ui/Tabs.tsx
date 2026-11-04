import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';

interface Tab {
  id: string;
  label: string;
  /** A count shown after the label — unread notifications, open tickets. */
  count?: number;
}

interface TabsProps {
  /*
   * Readonly, so a caller can pass a `const` array without copying it. A
   * mutable `Tab[]` would reject every literal declared `as const`, which is how
   * a fixed set of tabs should be declared.
   */
  tabs: readonly Tab[];
  active: string;
  onChange: (id: string) => void;
  /** An accessible name for the group, when the surrounding heading is not enough. */
  label?: string;
}

/**
 * A segmented control.
 *
 * Real `role="tab"` semantics rather than styled buttons: without them a screen
 * reader announces a row of unrelated buttons with no indication that one is
 * selected or that they control the same region.
 *
 * ## Why the pill is measured
 *
 * The selected pill is one absolutely-positioned element sliding under the
 * labels, so the movement is a transform on a single node rather than a
 * background repaint on each. An earlier version placed it arithmetically —
 * `width: (100% - 8px) / count`, `translateX(index * 100%)` — which is correct
 * only when every tab is the same width. They are not: the labels differ, some
 * carry a count badge, and the strip is a scroll container whose children
 * cannot shrink below their content. The pill therefore sat under "All" while
 * being as wide as "Completed".
 *
 * Measuring the active tab is the version that cannot drift. It costs one
 * layout read per selection change, plus a `ResizeObserver` so the pill follows
 * a strip that reflows — a font finishing loading, or the window narrowing.
 */
export function Tabs({ tabs, active, onChange, label }: TabsProps) {
  const activeIndex = Math.max(0, tabs.findIndex((tab) => tab.id === active));
  const stripRef = useRef<HTMLDivElement | null>(null);
  const [pill, setPill] = useState<{ left: number; width: number } | null>(null);

  const measure = useCallback(() => {
    const strip = stripRef.current;
    const target = strip?.querySelector<HTMLElement>('[data-tab-active="true"]');
    if (!strip || !target) return;
    // offsetLeft is relative to the strip's padding box and is unaffected by how
    // far the strip has been scrolled, which is exactly what the pill needs.
    setPill({ left: target.offsetLeft, width: target.offsetWidth });
  }, []);

  useLayoutEffect(measure, [measure, activeIndex, tabs.length]);

  useEffect(() => {
    const strip = stripRef.current;
    if (!strip || typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(measure);
    observer.observe(strip);
    return () => observer.disconnect();
  }, [measure]);

  /* Left/right arrows move between tabs, which is what a tablist is expected to
     do; without it a keyboard user has to tab through every option. */
  const onKeyDown = (event: React.KeyboardEvent) => {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return;
    event.preventDefault();
    const delta = event.key === 'ArrowRight' ? 1 : -1;
    // Wraps, so the ends are not dead.
    const next = tabs[(activeIndex + delta + tabs.length) % tabs.length];
    if (next) onChange(next.id);
  };

  return (
    <div className="tabs" role="tablist" aria-label={label} onKeyDown={onKeyDown} ref={stripRef}>
      {/* Hidden until measured, so it never flashes at the wrong size on mount. */}
      {pill && (
        <span
          className="tabs__pill"
          aria-hidden="true"
          style={{ width: `${pill.width}px`, transform: `translateX(${pill.left - 4}px)` }}
        />
      )}
      {tabs.map((tab) => (
        <button
          key={tab.id}
          type="button"
          role="tab"
          id={`tab-${tab.id}`}
          data-tab-active={tab.id === active}
          aria-selected={tab.id === active}
          aria-controls={`panel-${tab.id}`}
          /* Only the selected tab is in the tab order; the arrows reach the
             rest. This is the roving tabindex a tablist is supposed to have. */
          tabIndex={tab.id === active ? 0 : -1}
          className={`tabs__tab ${tab.id === active ? 'tabs__tab--active' : ''}`}
          onClick={() => onChange(tab.id)}
        >
          {tab.label}
          {tab.count !== undefined && tab.count > 0 && <span className="tabs__count">{tab.count}</span>}
        </button>
      ))}
    </div>
  );
}
