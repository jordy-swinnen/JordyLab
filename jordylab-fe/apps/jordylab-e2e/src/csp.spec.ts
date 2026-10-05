import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import type { Page } from '@playwright/test';
import { environment, expect, test } from './support/fixtures';
import { PAGES } from './support/pages';
import { pointAppAtKeycloak } from './support/session';

// The production Content-Security-Policy (the Report-Only header in deploy/containers/frontend/security-headers.conf, MRB-11) applied here as
// an ENFORCING header to every page of the signed-in app, so anything the policy would block shows up as a failing test instead of a line in
// the owner's console. In production the page origin is jordylab.be and 'self' covers the API and Keycloak; in this throwaway stack they are
// other origins, so exactly those two origins are added to default-src and connect-src, nothing else is relaxed.
// (This runs against the e2e build; tools/app-boot-check.mjs checks the optimised production bundle, including its one inline script.)
function productionPolicy(): string {
  const root = process.env['E2E_REPO_ROOT'];
  if (!root) {
    throw new Error('E2E_REPO_ROOT is not set: run this suite through jordylab-fe/e2e/run.sh web.');
  }
  const conf = readFileSync(join(root, 'deploy/containers/frontend/security-headers.conf'), 'utf8');
  const line = conf.split('\n').find((candidate) => candidate.startsWith('add_header Content-Security-Policy-Report-Only'));
  const policy = line?.match(/"([^"]+)"/)?.[1];
  if (!policy) {
    throw new Error('No Content-Security-Policy-Report-Only header found in security-headers.conf');
  }
  const stackOrigins = [environment.keycloakUrl, environment.apiOrigin].map((url) => new URL(url).origin).join(' ');

  return policy.replace(/(default-src|connect-src) 'self'/g, `$1 'self' ${stackOrigins}`);
}

const POLICY = productionPolicy();

/** What the browser said about this page besides the violation events: console errors and uncaught exceptions, to explain a page that did not render. */
const pageNotes = new WeakMap<Page, string[]>();
const pageViolations = new WeakMap<Page, string[]>();

/** Serves this page's own documents with the policy as an enforcing header and records every violation the browser reports. */
async function enforcePolicy(page: Page): Promise<void> {
  const notes: string[] = [];
  const violations: string[] = [];
  pageNotes.set(page, notes);
  pageViolations.set(page, violations);
  // exposeFunction reaches every frame, so a violation inside the silent-sign-in iframe is recorded too, not only the top page's.
  await page.exposeFunction('reportCspViolation', (violation: string) => violations.push(violation));
  page.on('console', (message) => message.type() === 'error' && notes.push(`console: ${message.text().slice(0, 300)}`));
  page.on('pageerror', (error) => notes.push(`exception: ${error.message.slice(0, 300)}`));
  await page.route(
    (url) => url.origin === new URL(environment.baseUrl).origin,
    async (route) => {
      if (route.request().resourceType() !== 'document') {
        return route.continue();
      }
      const response = await route.fetch();
      return route.fulfill({ response, headers: { ...response.headers(), 'content-security-policy': POLICY } });
    },
  );
  await page.addInitScript(() => {
    document.addEventListener('securitypolicyviolation', (event) => {
      const report = (window as unknown as { reportCspViolation: (violation: string) => void }).reportCspViolation;
      report(`${event.violatedDirective} blocked ${event.blockedURI || 'inline'} (${event.sourceFile || 'page'}:${event.lineNumber})`);
    });
  });
}

async function violationsOf(page: Page): Promise<string[]> {
  await page.waitForLoadState('networkidle');

  return [...(pageViolations.get(page) ?? [])];
}

/** Waits for the page's heading; if the app never renders, fails with what the browser blocked or complained about instead of "not found". */
async function expectRendered(page: Page, heading: Parameters<Page['getByRole']>[1]): Promise<void> {
  try {
    await expect(page.getByRole('heading', heading).first()).toBeVisible();
  } catch (error) {
    const violations = await violationsOf(page).catch(() => ['(could not read violations)']);
    throw new Error(`The page did not render under the policy.\nViolations: ${JSON.stringify(violations, null, 1)}\nBrowser notes: ${JSON.stringify(pageNotes.get(page) ?? [], null, 1)}\n${String(error)}`);
  }
}

test.describe('Content-Security-Policy (production policy, enforced)', () => {
  for (const target of PAGES) {
    test(`${target.name} loads with nothing blocked`, async ({ page }) => {
      await enforcePolicy(page);
      await page.goto(target.path);
      await expectRendered(page, { name: target.heading });

      expect(await violationsOf(page)).toEqual([]);
    });
  }

  test('a game detail page loads with nothing blocked', async ({ page }) => {
    await enforcePolicy(page);
    await page.goto('/games/grid');
    await page.getByRole('link', { name: /Chrono Trigger/ }).click();
    await expectRendered(page, { level: 2, name: 'Chrono Trigger' });

    expect(await violationsOf(page)).toEqual([]);
  });

  // Keycloak's silent sign-in loads this page in a hidden iframe on every app start; the e2e stack's session never reaches it, so it is opened
  // directly. Its script must be an external file: an inline one is refused by script-src and single sign-on silently stops (BUG-070).
  test('the silent sign-in page runs its script under the policy', async ({ page }) => {
    await enforcePolicy(page);
    await page.goto('/silent-check-sso.html');

    expect(await violationsOf(page)).toEqual([]);
  });

  test('signing in and out with the policy on works and blocks nothing', async ({ browser }) => {
    const context = await browser.newContext({ baseURL: environment.baseUrl, storageState: undefined });
    await pointAppAtKeycloak(context, environment.keycloakUrl);
    const page = await context.newPage();
    await enforcePolicy(page);

    await page.goto('/login');
    await expect(page.getByRole('button', { name: 'Sign in with Keycloak' })).toBeVisible();

    expect(await violationsOf(page)).toEqual([]);
    await context.close();
  });
});
