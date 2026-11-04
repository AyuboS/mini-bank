import type { ReactNode } from 'react';

/**
 * `success` is the previous spelling of `positive`, kept so the admin screens
 * keep compiling; both render identically. New code should say `positive`.
 */
type Tone = 'neutral' | 'positive' | 'success' | 'warning' | 'danger' | 'brand';

interface BadgeProps {
  tone?: Tone;
  children: ReactNode;
  /** A filled dot before the label, for a status that is a state rather than a count. */
  dot?: boolean;
}

export function Badge({ tone = 'neutral', children, dot = false }: BadgeProps) {
  return (
    <span className={`badge badge--${tone}`}>
      {dot && <span className="badge__dot" aria-hidden="true" />}
      {children}
    </span>
  );
}

/**
 * Maps a domain status onto a tone and a human label.
 *
 * Centralised so that PENDING is the same colour and the same word everywhere it
 * appears. The alternative — each screen deciding for itself — is how a transfer
 * ends up "Pending" in one place and "Awaiting review" in another.
 */
const STATUS: Record<string, { tone: Tone; label: string }> = {
  // Transfers
  COMPLETED: { tone: 'positive', label: 'Completed' },
  PENDING: { tone: 'warning', label: 'In review' },
  PROCESSING: { tone: 'warning', label: 'Processing' },
  FAILED: { tone: 'danger', label: 'Failed' },
  REVERSED: { tone: 'neutral', label: 'Reversed' },

  // Accounts and cards
  ACTIVE: { tone: 'positive', label: 'Active' },
  FROZEN: { tone: 'warning', label: 'Frozen' },
  CLOSED: { tone: 'neutral', label: 'Closed' },
  EXPIRED: { tone: 'neutral', label: 'Expired' },
  CANCELLED: { tone: 'neutral', label: 'Cancelled' },

  // Users
  LOCKED: { tone: 'danger', label: 'Locked' },
  SUSPENDED: { tone: 'danger', label: 'Suspended' },
  DELETED: { tone: 'neutral', label: 'Deleted' },

  // Standing orders
  SCHEDULED: { tone: 'brand', label: 'Scheduled' },
  PAUSED: { tone: 'neutral', label: 'Paused' },

  // Money requests
  PAID: { tone: 'positive', label: 'Paid' },
  DECLINED: { tone: 'neutral', label: 'Declined' },

  // Savings
  ACHIEVED: { tone: 'positive', label: 'Reached' },
  ARCHIVED: { tone: 'neutral', label: 'Archived' },

  // Budgets
  OK: { tone: 'positive', label: 'On track' },
  NEARING: { tone: 'warning', label: 'Close to limit' },
  OVER: { tone: 'danger', label: 'Over budget' },

  // Support
  OPEN: { tone: 'brand', label: 'Open' },
  AWAITING_CUSTOMER: { tone: 'warning', label: 'Needs your reply' },
  RESOLVED: { tone: 'positive', label: 'Resolved' },

  // Risk
  ALLOW: { tone: 'positive', label: 'Allowed' },
  REVIEW: { tone: 'warning', label: 'In review' },
  BLOCK: { tone: 'danger', label: 'Blocked' },
};

export function StatusBadge({ status }: { status: string }) {
  // An unknown status is shown rather than hidden: a badge that silently
  // disappears when the API adds a value is harder to notice than an odd label.
  const entry = STATUS[status] ?? { tone: 'neutral' as Tone, label: status };
  return (
    <Badge tone={entry.tone} dot>
      {entry.label}
    </Badge>
  );
}
