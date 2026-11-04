import type { TransactionCategory } from '../api/types';
import { categoryMeta } from '../utils/categories';

interface CategoryIconProps {
  category?: TransactionCategory | null;
  size?: 'sm' | 'md' | 'lg';
}

/**
 * The tinted glyph that stands in front of a transaction.
 *
 * Coloured by category rather than by counterparty, so a list becomes scannable
 * by colour before it is read — all the groceries are green, all the bills
 * amber. The colour is looked up in one table (utils/categories) so it is the
 * same here, in the analytics chart and on the budget page.
 */
export function CategoryIcon({ category, size = 'md' }: CategoryIconProps) {
  const meta = categoryMeta(category);
  return (
    <span
      className={`avatar avatar--${size} category-icon`}
      style={{ ['--avatar-accent' as string]: meta.colour }}
      /* Decorative: the category is named in text beside it wherever it is
         meaningful, and announcing the icon too would repeat it. */
      aria-hidden="true"
    >
      {meta.icon}
    </span>
  );
}
