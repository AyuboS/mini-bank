import { useState } from 'react';
import { Search } from 'lucide-react';
import type { TransactionCategory, TransferStatus } from '../api/types';
import { TRANSACTION_CATEGORIES } from '../api/types';
import { CATEGORY_META } from '../utils/categories';
import { NO_FILTERS, countActive, type TransactionFilters as Filters } from '../utils/transactionFilters';
import { Input, Select } from './ui';

interface TransactionFiltersProps {
  filters: Filters;
  onChange: (next: Filters) => void;
  /** A problem with the filters as typed, shown under the controls. */
  problem: string | null;
}

const STATUSES: { id: TransferStatus; label: string }[] = [
  { id: 'COMPLETED', label: 'Completed' },
  { id: 'PENDING', label: 'In review' },
  { id: 'FAILED', label: 'Failed' },
  { id: 'REVERSED', label: 'Reversed' },
];

/** Search and filters for the transaction list. */
export function TransactionFilters({ filters, onChange, problem }: TransactionFiltersProps) {
  const [expanded, setExpanded] = useState(false);
  const set = (patch: Partial<Filters>) => onChange({ ...filters, ...patch });
  const active = countActive({ ...filters, q: '' });

  return (
    <form className="filters" role="search" aria-label="Transactions" onSubmit={(event) => event.preventDefault()}>
      <div className="filters__top">
        <Input
          className="filters__search"
          type="search"
          aria-label="Search transactions"
          placeholder="Search description or reference"
          icon={<Search aria-hidden="true" />}
          maxLength={80}
          value={filters.q}
          onChange={(event) => set({ q: event.target.value })}
        />
        <button
          type="button"
          className="btn btn-secondary filters__toggle"
          aria-expanded={expanded}
          aria-controls="transaction-filters"
          onClick={() => setExpanded((current) => !current)}
        >
          Filters{active > 0 ? ` (${active})` : ''}
        </button>
      </div>

      <div className={`filters__more ${expanded ? 'filters__more--open' : ''}`} id="transaction-filters">
        <Input label="From" type="date" value={filters.from} max={filters.to || undefined} onChange={(event) => set({ from: event.target.value })} />
        <Input label="To" type="date" value={filters.to} min={filters.from || undefined} onChange={(event) => set({ to: event.target.value })} />
        <Select
          label="Category"
          value={filters.category}
          onChange={(event) => set({ category: event.target.value as TransactionCategory | '' })}
        >
          <option value="">Any</option>
          {TRANSACTION_CATEGORIES.map((category) => (
            <option key={category} value={category}>
              {CATEGORY_META[category].label}
            </option>
          ))}
        </Select>
        <Input
          label="Min amount"
          inputMode="decimal"
          placeholder="0.00"
          value={filters.minAmount}
          onChange={(event) => set({ minAmount: event.target.value.replace(',', '.') })}
        />
        <Input
          label="Max amount"
          inputMode="decimal"
          placeholder="Any"
          value={filters.maxAmount}
          onChange={(event) => set({ maxAmount: event.target.value.replace(',', '.') })}
        />
        <Select
          label="Status"
          value={filters.status}
          onChange={(event) => set({ status: event.target.value as TransferStatus | '' })}
        >
          <option value="">Any</option>
          {STATUSES.map((status) => (
            <option key={status.id} value={status.id}>
              {status.label}
            </option>
          ))}
        </Select>
      </div>

      {problem && (
        <p className="filters__problem" role="alert">
          {problem}
        </p>
      )}

      {countActive(filters) > 0 && (
        <button type="button" className="link filters__clear" onClick={() => onChange(NO_FILTERS)}>
          Clear filters
        </button>
      )}
    </form>
  );
}
