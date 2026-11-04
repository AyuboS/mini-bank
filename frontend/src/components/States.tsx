import type { ReactNode } from 'react';
import { CircleSlash, RefreshCw, WifiOff } from 'lucide-react';
import { Button } from './ui';

interface EmptyStateProps {
  title: string;
  body?: string;
  icon?: ReactNode;
  action?: ReactNode;
}

/**
 * Nothing here yet.
 *
 * An empty state says two things: that the screen is working, and what would
 * put something in it. A blank panel says neither, and reads as a failure.
 */
export function EmptyState({ title, body, icon, action }: EmptyStateProps) {
  return (
    <div className="state">
      <span className="state__icon">{icon ?? <CircleSlash aria-hidden="true" />}</span>
      <p className="state__title">{title}</p>
      {body && <p className="state__body">{body}</p>}
      {action}
    </div>
  );
}

interface ErrorStateProps {
  title?: string;
  body?: string;
  onRetry?: () => void;
}

/**
 * A failure the customer can do something about, rather than a red wall.
 *
 * Announced as an alert, because a screen that silently swaps its content for an
 * error leaves a screen-reader user believing it is still loading.
 */
export function ErrorState({ title = "We couldn't load this", body, onRetry }: ErrorStateProps) {
  return (
    <div className="state" role="alert">
      <span className="state__icon">
        <WifiOff aria-hidden="true" />
      </span>
      <p className="state__title">{title}</p>
      {body && <p className="state__body">{body}</p>}
      {onRetry && (
        <Button variant="secondary" onClick={onRetry} icon={<RefreshCw aria-hidden="true" />}>
          Try again
        </Button>
      )}
    </div>
  );
}
