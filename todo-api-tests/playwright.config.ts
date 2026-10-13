import { defineConfig } from '@playwright/test';

/**
 * Playwright config for API testing of the To-Do List API.
 * No `webServer` is configured on purpose: the Spring Boot app depends on a
 * real Oracle database, so it must be started manually before running the
 * tests (see README.md). Playwright here only drives HTTP requests against
 * an already-running instance.
 */
export default defineConfig({
  testDir: './tests',

  // Tests hit one shared Oracle database. Running sequentially (1 worker)
  // keeps results deterministic and avoids flaky cross-test interference,
  // even though each test already uses unique generated data.
  workers: 1,
  fullyParallel: false,

  retries: 0,
  timeout: 30_000,
  expect: {
    timeout: 5_000,
  },

  reporter: [
    ['list'],
    ['html', { open: 'never', outputFolder: 'playwright-report' }],
  ],

  use: {
    baseURL: process.env.API_BASE_URL || 'http://localhost:8080',
    extraHTTPHeaders: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    trace: 'retain-on-failure',
  },
});
