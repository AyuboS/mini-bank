// Captures every screen at phone and desktop width, and reports horizontal overflow.
//
//   node scripts/screenshots.mjs <label>        e.g. before, after
//
// Needs the dev server and the API running. Credentials come from the
// environment so none are stored here:
//   SHOT_USER, SHOT_PASSWORD            a customer
//   SHOT_ADMIN, SHOT_ADMIN_PASSWORD     an administrator (optional)
import { mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from '@playwright/test';

const BASE = process.env.SHOT_BASE_URL ?? 'http://localhost:5173';
const label = process.argv[2] ?? 'current';
const outDir = resolve(dirname(fileURLToPath(import.meta.url)), '../../docs/screenshots', label);

const WIDTHS = [
  { name: '375', width: 375, height: 812 },
  { name: '1440', width: 1440, height: 900 },
];

const PUBLIC = ['/login', '/register', '/forgot-password'];
const CUSTOMER = [
  '/',
  '/accounts',
  '/top-up',
  '/cards',
  '/payments/send',
  '/transactions',
  '/analytics',
  '/savings',
  '/payments/scheduled',
  '/payments/requests',
  '/payments/payees',
  '/notifications',
  '/statements',
  '/support',
  '/security',
  '/settings',
  '/profile',
];
const ADMIN = ['/admin', '/admin/transfers', '/admin/users', '/admin/accounts', '/admin/audit'];

// SHOT_ONLY=/,/cards limits a run to those paths.
const only = process.env.SHOT_ONLY?.split(',');
const pick = (paths) => (only ? paths.filter((path) => only.includes(path)) : paths);

const fileName = (path) => (path === '/' ? 'home' : path.slice(1).replaceAll('/', '-'));

async function signIn(username, password) {
  const response = await fetch(`${BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (!response.ok) throw new Error(`Sign-in as ${username} failed with ${response.status}`);
  return response.json();
}

async function capture(browser, paths, tokens) {
  const overflowing = [];
  for (const size of WIDTHS) {
    const context = await browser.newContext({ viewport: { width: size.width, height: size.height } });
    if (tokens) {
      await context.addInitScript((session) => {
        localStorage.setItem('minibank.accessToken', session.accessToken);
        localStorage.setItem('minibank.refreshToken', session.refreshToken);
      }, tokens);
    }
    const page = await context.newPage();
    for (const path of paths) {
      await page.goto(`${BASE}${path}`, { waitUntil: 'load' });
      await page.waitForTimeout(2000); // data loads and entrance animations finish
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      );
      if (overflow > 0) overflowing.push(`${path} at ${size.name}px: ${overflow}px wider than the screen`);
      await page.screenshot({ path: resolve(outDir, `${fileName(path)}-${size.name}.png`), fullPage: true });
    }
    await context.close();
  }
  return overflowing;
}

const { SHOT_USER, SHOT_PASSWORD, SHOT_ADMIN, SHOT_ADMIN_PASSWORD } = process.env;
if (!SHOT_USER || !SHOT_PASSWORD) {
  console.error('Set SHOT_USER and SHOT_PASSWORD to a customer login.');
  process.exit(1);
}

await mkdir(outDir, { recursive: true });
const browser = await chromium.launch();
const overflowing = [
  ...(await capture(browser, pick(PUBLIC), null)),
  ...(await capture(browser, pick(CUSTOMER), await signIn(SHOT_USER, SHOT_PASSWORD))),
  ...(SHOT_ADMIN && SHOT_ADMIN_PASSWORD
    ? await capture(browser, pick(ADMIN), await signIn(SHOT_ADMIN, SHOT_ADMIN_PASSWORD))
    : []),
];
await browser.close();

console.log(`Saved to ${outDir}`);
console.log(overflowing.length ? `Horizontal overflow:\n  ${overflowing.join('\n  ')}` : 'No horizontal overflow.');
