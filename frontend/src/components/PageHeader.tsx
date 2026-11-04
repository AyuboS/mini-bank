import type { ReactNode } from 'react';

interface PageHeaderProps {
  title: string;
  /** One line saying what the screen is for. Omitted where the title is obvious. */
  subtitle?: string;
  /** The screen's primary action, and at most one secondary. */
  actions?: ReactNode;
}

/**
 * The title block every screen opens with.
 *
 * `h1` per page, always. The desktop chrome deliberately carries no page title,
 * so this is the document's only top-level heading and the anchor a screen
 * reader's heading navigation lands on.
 */
export function PageHeader({ title, subtitle, actions }: PageHeaderProps) {
  return (
    <header className="page-header">
      <div className="stack">
        <h1>{title}</h1>
        {subtitle && <p className="page-header__subtitle">{subtitle}</p>}
      </div>
      {actions && <div className="page-header__actions">{actions}</div>}
    </header>
  );
}
