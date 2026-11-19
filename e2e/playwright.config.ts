import { defineConfig, devices } from '@playwright/test';
import { E2E_API_PORT } from './support/backend';

// End-to-end tests against the real stack: the backend jar (started and
// restarted by the tests themselves, see support/backend.ts), PostgreSQL and
// the production frontend build served by `vite preview` (which proxies /api).
export default defineConfig({
  testDir: './tests',
  outputDir: './test-results',
  timeout: 15 * 60_000,
  fullyParallel: false,
  workers: 1, // tests share one backend process and port
  retries: 0,
  reporter: process.env.CI ? [['github'], ['list']] : [['list']],
  globalSetup: './support/global-setup.ts',
  globalTeardown: './support/global-teardown.ts',
  use: {
    baseURL: 'http://localhost:4173',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'npm --prefix ../frontend run build && npm --prefix ../frontend run preview -- --strictPort',
    url: 'http://localhost:4173',
    reuseExistingServer: !process.env.CI,
    timeout: 300_000,
    env: { API_PROXY_TARGET: `http://localhost:${E2E_API_PORT}` },
  },
  projects: [{ name: 'desktop', use: { ...devices['Desktop Chrome'] } }],
});
