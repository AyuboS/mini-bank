import { expect, test, type Page } from '@playwright/test';

// The seeded demo customers (backend DemoDataSeeder). Override for another stack.
const SENDER = { username: process.env.E2E_USER ?? 'customer.a', password: process.env.E2E_PASSWORD ?? 'Customer#2026!A' };
const RECIPIENT = {
  username: process.env.E2E_RECIPIENT ?? 'customer.b',
  password: process.env.E2E_RECIPIENT_PASSWORD ?? 'Customer#2026!B',
};
// Where the API answers. The dev server proxies /api; the docker frontend talks to :8080.
const API = process.env.E2E_API_URL ?? '';

async function recipientAccountNumber(page: Page): Promise<string> {
  const login = await page.request.post(`${API}/api/v1/auth/login`, { data: RECIPIENT });
  expect(login.status(), 'recipient sign-in').toBe(200);
  const { accessToken } = await login.json();
  const accounts = await page.request.get(`${API}/api/v1/accounts`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  const list: { accountNumber: string; purpose: string }[] = await accounts.json();
  return list.find((account) => account.purpose === 'SPENDING')!.accountNumber;
}

async function signIn(page: Page) {
  await page.goto('/login');
  await page.getByRole('tab', { name: 'Password' }).click();
  await page.getByLabel('Username, email or phone').fill(SENDER.username);
  await page.getByLabel('Password', { exact: true }).fill(SENDER.password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByText('Available to spend')).toBeVisible();
}

test('sign in, top up, send, and see both in the history', async ({ page }) => {
  const to = await recipientAccountNumber(page);
  const note = `E2E ${Date.now()}`;

  await signIn(page);

  // Simulated top-up.
  await page.goto('/top-up');
  await expect(page.getByRole('heading', { name: 'Simulated top-up' })).toBeVisible();
  await page.getByLabel('Amount').fill('12.34');
  await page.getByRole('button', { name: 'Continue' }).click();
  await page.getByRole('button', { name: /^Add / }).click();
  await expect(page.getByText('Simulated top-up added')).toBeVisible();
  const topUpReference = (await page.locator('.send-summary__reference').innerText()).trim();
  expect(topUpReference).toMatch(/^TRX-/);

  // Send money.
  await page.goto('/payments/send');
  await page.getByLabel('Account number').fill(to);
  await expect(page.locator('.send-lookup__name')).toBeVisible();
  await page.getByRole('button', { name: 'Continue' }).click();
  await page.getByLabel('Amount').fill('5.67');
  await page.getByLabel('Reference (optional)').fill(note);
  await page.getByRole('button', { name: 'Continue' }).click();
  await page.getByRole('button', { name: /^Send / }).click();
  await expect(page.getByText('Payment sent')).toBeVisible();
  const sendReference = (await page.locator('.send-summary__reference').innerText()).trim();
  expect(sendReference).not.toBe(topUpReference);

  // Both appear in the history, found by their references.
  for (const reference of [topUpReference, sendReference]) {
    await page.goto(`/transactions?q=${encodeURIComponent(reference)}`);
    const expected = reference === sendReference ? note : 'Simulated top-up';
    await expect(page.getByText(expected).first()).toBeVisible();
  }
});
