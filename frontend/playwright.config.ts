import { defineConfig, devices } from '@playwright/test';

// Runs against a stack that is already up: the Vite dev server by default,
// or the docker compose frontend in CI (E2E_BASE_URL=http://localhost:3000).
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  // One at a time: each run signs in twice and the login endpoint allows five a minute.
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'phone', use: { ...devices['Pixel 7'] } },
  ],
});
