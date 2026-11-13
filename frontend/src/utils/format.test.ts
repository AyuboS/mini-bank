import { describe, expect, it } from 'vitest';
import { formatMoney, formatSignedMoney, maskAccountNumber, splitMoney } from './format';

describe('formatMoney', () => {
  it.each([
    ['4250.5000', 'EUR', '€4,250.50'],
    ['0', 'USD', '$0.00'],
    ['1234567.8', 'GBP', '£1,234,567.80'],
    ['not a number', 'EUR', '€0.00'],
  ] as const)('%s %s is %s', (amount, currency, expected) => {
    expect(formatMoney(amount, currency)).toBe(expected);
  });
});

describe('splitMoney', () => {
  it('splits off the minor units so they can be set smaller', () => {
    expect(splitMoney('4657.92', 'EUR')).toEqual(['€4,657', '.92']);
  });
});

describe('formatSignedMoney', () => {
  it('uses a real minus sign for money out', () => {
    expect(formatSignedMoney('48.2', 'EUR', 'out')).toBe('−€48.20');
    expect(formatSignedMoney('48.2', 'EUR', 'in')).toBe('+€48.20');
  });
});

describe('maskAccountNumber', () => {
  it('keeps the last four digits', () => {
    expect(maskAccountNumber('MB-123456785671')).toBe('•••• 5671');
  });
  it('names the system account instead of masking it', () => {
    expect(maskAccountNumber('MB-SYSTEM-EUR')).toBe('MBank');
  });
});
