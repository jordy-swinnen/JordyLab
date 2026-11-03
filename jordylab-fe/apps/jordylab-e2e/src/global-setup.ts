import { chromium } from '@playwright/test';
import { ingestCatalog } from './support/catalog-api';
import { requiredEnvironment } from './support/environment';
import { signIn } from './support/session';

/**
 * Once per run. Signs in as the bootstrap admin through the app's real form and saves the session for every journey, then
 * creates one machine token through the admin API (spec 019 US5) and fills the throwaway catalog with it. Everything else
 * is created by the journeys themselves, through the UI.
 */
export default async function globalSetup(): Promise<void> {
  const environment = requiredEnvironment();

  const browser = await chromium.launch();
  const context = await browser.newContext({ baseURL: environment.baseUrl });
  const page = await context.newPage();
  try {
    // The bootstrap admin is created by an ApplicationRunner just after the context is up; retry the first sign-in briefly.
    await signInWithRetry(page, environment.adminUsername, environment.adminPassword);
  } catch (error) {
    // A failed sign-in must explain itself: where the browser ended up and which form fields it could see (no values).
    const fields = await page.locator('input, button').evaluateAll((elements) =>
      elements.map(
        (element) =>
          `${element.tagName.toLowerCase()}[name=${element.getAttribute('name')} type=${element.getAttribute('type')}]`,
      ),
    );
    await page.screenshot({ path: 'test-results/global-setup-sign-in-failure.png' });
    console.error(`Global setup could not sign in. Page: ${page.url()}\nFields: ${fields.join(', ')}`);
    await browser.close();
    throw error;
  }

  const csrfResponse = await context.request.get(`${environment.apiOrigin}/api/identity/auth/csrf`);
  if (!csrfResponse.ok()) {
    throw new Error(`Could not bootstrap CSRF for the machine token (HTTP ${csrfResponse.status()})`);
  }
  const xsrf = (await context.cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value;
  if (!xsrf) {
    throw new Error('No XSRF-TOKEN cookie after the CSRF bootstrap');
  }
  const created = await context.request.post(`${environment.apiOrigin}/api/identity/machines`, {
    headers: { 'X-XSRF-TOKEN': xsrf },
    data: { name: 'e2e-machine' },
  });
  if (!created.ok()) {
    throw new Error(`Could not create the e2e machine token (HTTP ${created.status()}): ${await created.text()}`);
  }
  const machine = (await created.json()) as { token: string };

  await ingestCatalog(context.request, environment, machine.token);

  await context.storageState({ path: '.auth/admin.json' });
  await browser.close();
}

/** The backend's health endpoint can answer a moment before the bootstrap admin exists; retry only that first sign-in. */
async function signInWithRetry(
  page: Parameters<typeof signIn>[0],
  username: string,
  password: string,
): Promise<void> {
  for (let attempt = 1; attempt <= 6; attempt++) {
    try {
      await signIn(page, username, password);

      return;
    } catch (error) {
      if (attempt === 6) {
        throw error;
      }
      await page.waitForTimeout(1_000);
    }
  }
}
