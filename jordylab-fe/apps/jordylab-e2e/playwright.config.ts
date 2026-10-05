import { defineConfig, devices } from '@playwright/test';
import { requiredEnvironment } from './src/support/environment';

// Run through jordylab-fe/e2e/run.sh web: it builds the app fresh, starts the throwaway Postgres + Keycloak + backend and exports
// the E2E_* variables read here. Nothing in this config points at a fixed address or the dev stack.
const environment = requiredEnvironment();

export default defineConfig({
  testDir: './src',
  testMatch: '**/*.spec.ts',
  globalSetup: './src/global-setup.ts',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  // One shared throwaway database: journeys run one after another, in file order.
  fullyParallel: false,
  workers: 1,
  retries: process.env['CI'] ? 1 : 0,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  outputDir: 'test-results',
  use: {
    baseURL: environment.baseUrl,
    storageState: '.auth/admin.json',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', testIgnore: '**/csp.spec.ts', use: { ...devices['Desktop Chrome'] } },
    {
      // csp.spec.ts serves each document through route.fulfill() to add the policy header. Chrome then has no address for the document, treats it
      // as public and its Local Network Access check refuses the page's requests to the throwaway Keycloak on localhost. That check is a
      // browser feature, not part of the policy under test, so only this project switches it off.
      name: 'chromium-csp',
      testMatch: '**/csp.spec.ts',
      use: { ...devices['Desktop Chrome'], launchOptions: { args: ['--disable-features=LocalNetworkAccessChecks'] } },
    },
  ],
});
