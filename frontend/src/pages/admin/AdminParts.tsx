import type { PageResponse } from '../../api/types';
import { Button } from '../../components/ui';

interface FilterOption<T extends string> {
  id: T;
  label: string;
}

interface FilterRowProps<T extends string> {
  label: string;
  options: readonly FilterOption<T>[];
  value: T;
  onChange: (value: T) => void;
}

/** A row of text buttons; the chosen one is underlined. */
export function FilterRow<T extends string>({ label, options, value, onChange }: FilterRowProps<T>) {
  return (
    <div className="filter-row" role="group" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.id || 'all'}
          type="button"
          className="filter-row__option"
          aria-pressed={option.id === value}
          onClick={() => onChange(option.id)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

interface AdminPagerProps {
  page: PageResponse<unknown>;
  noun: string;
  onPage: (page: number) => void;
}

export function AdminPager({ page, noun, onPage }: AdminPagerProps) {
  return (
    <div className="admin-pager">
      <span>
        Page {page.page + 1} of {Math.max(1, page.totalPages)} · {page.totalElements} {noun}
      </span>
      <Button size="sm" variant="ghost" disabled={page.first} onClick={() => onPage(Math.max(0, page.page - 1))}>
        Previous
      </Button>
      <Button size="sm" variant="ghost" disabled={page.last} onClick={() => onPage(page.page + 1)}>
        Next
      </Button>
    </div>
  );
}
