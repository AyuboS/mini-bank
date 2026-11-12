import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { transfersApi } from '../api/transfers';
import type { PageResponse, TransactionResponse } from '../api/types';
import { ErrorState } from '../components/States';
import { TransactionDetail } from '../components/TransactionDetail';
import { TransactionFilters } from '../components/TransactionFilters';
import { TransactionList } from '../components/TransactionList';
import { TransactionTable } from '../components/TransactionTable';
import { Button, Skeleton } from '../components/ui';
import { useAsync } from '../hooks/useAsync';
import { useDebounced } from '../hooks/useDebounced';
import { useIsPhone } from '../hooks/useMediaQuery';
import { toDateKey } from '../utils/format';
import {
  NO_FILTERS,
  countActive,
  filterProblem,
  toTransactionQuery,
  type TransactionFilters as Filters,
} from '../utils/transactionFilters';

const PAGE_SIZE = 25;

// A Map keeps the days in the order the API sent them.
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

function dayHeading(key: string): string {
  const date = new Date(`${key}T00:00:00`);
  const today = new Date();
  const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const distance = Math.round((startOfDay(today) - startOfDay(date)) / 86_400_000);

  if (distance === 0) return 'Today';
  if (distance === 1) return 'Yesterday';
  return date.toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
}

export function TransactionsPage() {
  const [searchParams] = useSearchParams();
  const linkedSearch = searchParams.get('q') ?? '';

  const [filters, setFilters] = useState<Filters>({ ...NO_FILTERS, q: linkedSearch });
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<TransactionResponse | null>(null);
  const isPhone = useIsPhone();

  // The command palette links here with ?q=..., also while this screen is already open.
  useEffect(() => {
    setFilters((current) => (current.q === linkedSearch ? current : { ...current, q: linkedSearch }));
    setPage(0);
  }, [linkedSearch]);

  const changeFilters = (next: Filters) => {
    setFilters(next);
    setPage(0);
  };

  // Filtering happens in the database. Waiting 300ms keeps typing from sending a request per key.
  const settled = useDebounced(filters, 300);
  const problem = filterProblem(settled);
  const query = useMemo(() => (problem ? null : toTransactionQuery(settled)), [settled, problem]);

  const { data, loading, error, reload, setData } = useAsync<PageResponse<TransactionResponse> | null>(
    async () => (query ? transfersApi.transactions({ ...query, page, size: PAGE_SIZE }) : null),
    [query, page],
  );

  const rows = data?.content ?? [];
  const filtered = countActive(filters) > 0;

  return (
    <>
      <div className="page__head page__head--wide-only">
        <h1 className="page__title">Transactions</h1>
      </div>

      <TransactionFilters filters={filters} onChange={changeFilters} problem={problem} />

      {loading && (
        <div className="home-loading" role="status" aria-busy="true">
          <span className="sr-only">Loading transactions</span>
          {Array.from({ length: 8 }, (_, index) => (
            <Skeleton key={index} height="1.25rem" />
          ))}
        </div>
      )}

      {!loading && error && <ErrorState title={error.title} body={error.body} onRetry={reload} />}

      {!loading && !error && data && rows.length === 0 && (
        <p className="home-empty">
          {filtered
            ? 'No transactions match these filters.'
            : 'No transactions yet. They appear here as soon as money moves.'}
        </p>
      )}

      {!loading && data && rows.length > 0 && (
        <>
          <p className="txn-count" aria-live="polite">
            {data.totalElements} {data.totalElements === 1 ? 'transaction' : 'transactions'}
          </p>

          {isPhone ? (
            byDay(rows).map(([day, group]) => (
              <section className="day-group" key={day} aria-label={dayHeading(day)}>
                <h2 className="day-group__title">{dayHeading(day)}</h2>
                <TransactionList transactions={group} onSelect={setSelected} groupedByDay />
              </section>
            ))
          ) : (
            <TransactionTable transactions={rows} onSelect={setSelected} />
          )}

          {data.totalPages > 1 && (
            <nav className="pager pager--plain" aria-label="Pages">
              <Button variant="secondary" size="sm" disabled={data.first} onClick={() => setPage(page - 1)}>
                Previous
              </Button>
              <span>
                Page {data.page + 1} of {data.totalPages}
              </span>
              <Button variant="secondary" size="sm" disabled={data.last} onClick={() => setPage(page + 1)}>
                Next
              </Button>
            </nav>
          )}
        </>
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
