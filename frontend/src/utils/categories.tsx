import {
  Banknote,
  Bus,
  Clapperboard,
  CreditCard,
  GraduationCap,
  HeartPulse,
  PiggyBank,
  Plane,
  Receipt,
  ShoppingBag,
  ShoppingCart,
  UtensilsCrossed,
  MoreHorizontal,
} from 'lucide-react';
import type { ReactNode } from 'react';
import type { TransactionCategory } from '../api/types';

interface CategoryMeta {
  label: string;
  icon: ReactNode;
  /** The CSS variable the category is drawn in, in charts and on avatars. */
  colour: string;
  /** Whether money in this category left the customer's control. */
  spending: boolean;
}

/** Everything the interface needs to know about a category, in one table. */
export const CATEGORY_META: Record<TransactionCategory, CategoryMeta> = {
  GROCERIES: { label: 'Groceries', icon: <ShoppingCart />, colour: 'var(--c-green)', spending: true },
  DINING: { label: 'Dining', icon: <UtensilsCrossed />, colour: 'var(--c-coral)', spending: true },
  SHOPPING: { label: 'Shopping', icon: <ShoppingBag />, colour: 'var(--c-magenta)', spending: true },
  TRANSPORT: { label: 'Transport', icon: <Bus />, colour: 'var(--c-blue)', spending: true },
  BILLS: { label: 'Bills', icon: <Receipt />, colour: 'var(--c-amber)', spending: true },
  ENTERTAINMENT: { label: 'Entertainment', icon: <Clapperboard />, colour: 'var(--c-violet)', spending: true },
  TRAVEL: { label: 'Travel', icon: <Plane />, colour: 'var(--c-teal)', spending: true },
  HEALTH: { label: 'Health', icon: <HeartPulse />, colour: 'var(--c-coral)', spending: true },
  EDUCATION: { label: 'Education', icon: <GraduationCap />, colour: 'var(--c-lime)', spending: true },
  SALARY: { label: 'Salary', icon: <Banknote />, colour: 'var(--c-green)', spending: false },
  SAVINGS: { label: 'Savings', icon: <PiggyBank />, colour: 'var(--c-teal)', spending: false },
  TRANSFER: { label: 'Transfer', icon: <CreditCard />, colour: 'var(--c-blue)', spending: false },
  OTHER: { label: 'Other', icon: <MoreHorizontal />, colour: 'var(--ink-3)', spending: true },
};

/** What an uncategorised payment is called and drawn as. */
export const UNCATEGORISED: CategoryMeta = {
  label: 'Uncategorised',
  icon: <MoreHorizontal />,
  colour: 'var(--ink-4)',
  spending: true,
};

/** The metadata for a category, including the null case. */
export function categoryMeta(category?: TransactionCategory | null): CategoryMeta {
  return category ? CATEGORY_META[category] ?? UNCATEGORISED : UNCATEGORISED;
}
