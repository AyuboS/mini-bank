import { describe, expect, it } from 'vitest';
import { cleanAmountInput, fromMinorUnits, sumMinorUnits, toMinorUnits } from './money';

describe('toMinorUnits', () => {
  it.each([
    ['4250.5000', 425050],
    ['0.10', 10],
    ['0.1', 10],
    ['12', 1200],
    ['0.07', 7],
    ['-48.20', -4820],
    [4657.92, 465792],
    [800, 80000],
  ])('%s is %i cents', (amount, expected) => {
    expect(toMinorUnits(amount)).toBe(expected);
  });

  it('rounds a third decimal half away from zero', () => {
    expect(toMinorUnits('1.005')).toBe(101);
    expect(toMinorUnits('1.0049')).toBe(100);
    expect(toMinorUnits('-1.005')).toBe(-101);
  });

  it('treats missing or malformed input as zero', () => {
    expect(toMinorUnits(undefined)).toBe(0);
    expect(toMinorUnits('abc')).toBe(0);
    expect(toMinorUnits('')).toBe(0);
  });
});

describe('sumMinorUnits', () => {
  it('adds the amounts that floating point gets wrong', () => {
    expect(0.1 + 0.2).not.toBe(0.3);
    expect(sumMinorUnits(['0.10', '0.20'])).toBe(30);
  });

  it('adds account balances exactly', () => {
    expect(sumMinorUnits(['4657.92', '650.00', '0.08'])).toBe(530800);
  });

  it('is zero for an empty list', () => {
    expect(sumMinorUnits([])).toBe(0);
  });
});

describe('fromMinorUnits', () => {
  it.each([
    [425050, '4250.50'],
    [7, '0.07'],
    [0, '0.00'],
    [-4820, '-48.20'],
  ])('%i cents is %s', (minor, expected) => {
    expect(fromMinorUnits(minor)).toBe(expected);
  });
});

describe('cleanAmountInput', () => {
  it.each([
    ['12,5', '12.5'],
    ['12.345', '12.34'],
    ['1.2.3', '1.23'],
    ['€ 40', '40'],
    ['abc', ''],
    ['-5', '5'],
  ])('%s becomes %s', (typed, expected) => {
    expect(cleanAmountInput(typed)).toBe(expected);
  });
});
