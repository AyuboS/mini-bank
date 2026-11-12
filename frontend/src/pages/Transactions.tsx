import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ChevronLeft, ChevronRight, Receipt } from 'lucide-react';
import { transfersApi } from '../api/transfers';
import type { PageResponse, TransactionResponse, TransferStatus } from '../api/types';
import { useAsync } from '../hooks/useAsync';
import { TransactionDetail } from '../components/TransactionDetail';
import { TransactionRow } from '../components/TransactionRow';
import { EmptyState, ErrorState } from '../components/States';
import { Button, Surface, Skeleton, Tabs } from '../components/ui';
import { toDateKey } from '../utils/format';

const FILTERS = [
  { id: '', label: 'All' },
  { id: 'COMPLETED', label: 'Completed' },
  { id: 'PENDING', label: 'In review' },
  { id: 'FAILED', label: 'Failed' },
  { id: 'REVERSED', label: 'Reversed' },
] as const;

type Filter = (typeof FILTERS)[number]['id'];

const PAGE_SIZE = 12;

/**
 * Splits a page of transactions into days, keeping the order the API sent them.
 *
 * A `Map` rather than an object: it preserves insertion order for string keys
 * that look like dates, which a plain object does not guarantee, and getting
 * that wrong reorders somebody's statement.
 */
function byDay(transactions: TransactionResponse[]): [string, TransactionResponse[]][] {
  const groups = new Map<string, TransactionResponse[]>();
  for (const transaction of transactions) {
    const key = toDateKey(new Date(transaction.createdAt));
    const bucket = groups.get(key);
    if (bucket) bucket.push(transaction);
    else groups.set(key, [transaction]);
  }
  return [...groups.entries()];
}

/** "Today", "Yesterday", then a weekday, then a date — the way a statement reads. */
function dayHeading(key: string): string {
  const date = new Date(`${key}T00:00:00`);
  const today = new Date();
  const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const distance = Math.round((startOfDay(today) - startOfDay(date)) / 86_400_000);

  if (distance === 0) return 'Today';
  if (distance === 1) return 'Yesterday';
  if (distance < 7) return date.toLocaleDateString(undefined, { weekday: 'long' });
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' });
}

export function TransactionsPage() {
  const [searchParams] = useSearchParams();
  const query = searchParams.get('q')?.toLowerCase() ?? '';

  const [filter, setFilter] = useState<Filter>('');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<TransactionResponse | null>(null);

  const { data, loading, error, reload, setData } = useAsync<PageResponse<TransactionResponse>>(
    () => transfersApi.transactions({ page, size: PAGE_SIZE, status: filter as TransferStatus | '' }),
    [page, filter],
  );

  /*
    Paging and status filtering happen in the database. The text search is a
    client-side narrowing of the page in hand, because the API has no free-text
    parameter — so it is presented as filtering what is shown, not as a search
    across the whole history.
  */
  const visible = query
    ? (data?.content ?? []).filter(
        (txn) =>
          txn.reference.toLowerCase().includes(query) ||
          (txn.description ?? '').toLowerCase().includes(query) ||
          txn.counterpartyAccountNumber.toLowerCase().includes(query),
      )
    : (data?.content ?? []);

  return (
    <>
      <div className="page__head">
        <h1 className="page__title">Transactions</h1>
        <p className="page__subtitle">Everything moving in and out of your accounts.</p>
      </div>

      <Surface tone="chrome" className="filter-bar">
        <Tabs
          tabs={FILTERS}
          active={filter}
          label="Filter transactions by status"
          onChange={(value) => {
            setFilter(value as Filter);
            setPage(0);
          }}
        />
        {query && (
          <span className="small dim">
            Filtering this page by &ldquo;{query}&rdquo;
          </span>
        )}
      </Surface>

      {loading && (
        <div className="stack">
          {Array.from({ length: 6 }, (_, index) => (
            <Skeleton key={index} height={72} />
          ))}
        </div>
      )}

      {!loading && (error || !data) && (
        <ErrorState body={error?.body ?? 'We could not load your transactions.'} onRetry={reload} />
      )}

      {!loading && data && visible.length === 0 && (
        <Surface>
          <EmptyState
            title={query || filter ? 'Nothing matches' : 'No transactions yet'}
            body={
              query || filter
                ? 'Try a different filter.'
                : 'Your activity will appear here as soon as money moves.'
            }
            icon={<Receipt aria-hidden="true" />}
          />
        </Surface>
      )}

      {!loading && data && visible.length > 0 && (
        <Surface className="list-panel">
          {/*
            Grouped by day, with the date on the heading rather than on every
            row. Twelve rows each repeating "Today, 12:12" is a table; twelve
            rows under one heading is a statement.
          */}
          {byDay(visible).map(([day, group]) => (
            <div className="txn-group" key={day}>
              <h2 className="txn-day">{dayHeading(day)}</h2>
              {group.map((txn) => (
                <TransactionRow key={txn.transferId} transaction={txn} onSelect={setSelected} hideDate />
              ))}
            </div>
          ))}

          <div className="pager">
            <Button
              variant="ghost"
              disabled={data.first}
              onClick={() => setPage((current) => Math.max(0, current - 1))}
              icon={<ChevronLeft aria-hidden="true" />}
            >
              Previous
            </Button>
            <span className="small dim">
              Page {data.page + 1} of {Math.max(1, data.totalPages)} · {data.totalElements} total
            </span>
            <Button
              variant="ghost"
              disabled={data.last}
              onClick={() => setPage((current) => current + 1)}
            >
              Next
              <ChevronRight aria-hidden="true" />
            </Button>
          </div>
        </Surface>
      )}

      <TransactionDetail
        transaction={selected}
        onClose={() => setSelected(null)}
        onRecategorised={(updated) => {
          setSelected(updated);
          setData((current) =>
            current && {
              ...current,
              content: current.content.map((row) => (row.transferId === updated.transferId ? updated : row)),
            },
          );
        }}
      />
    </>
  );
}
