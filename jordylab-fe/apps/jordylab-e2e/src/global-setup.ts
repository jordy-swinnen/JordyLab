import { chromium, request } from '@playwright/test';
import { ingestCatalog } from './support/catalog-api';
import { requiredEnvironment } from './support/environment';
import { pointAppAtKeycloak, signInThroughKeycloak } from './support/session';

/**
 * Once per run: sign in through Keycloak as the admin test user and save the session for every journey, and fill the throwaway
 * catalog through the app's scan endpoint. Everything else is created by the journeys themselves, through the UI.
 */
export default async function globalSetup(): Promise<void> {
  const environment = requiredEnvironment();

  const apiRequest = await request.newContext();
  await ingestCatalog(apiRequest, environment);
  await apiRequest.dispose();

  const browser = await chromium.launch();
  const context = await browser.newContext({ baseURL: environment.baseUrl });
  await pointAppAtKeycloak(context, environment.keycloakUrl);
  const page = await context.newPage();
  try {
    await signInThroughKeycloak(page, environment.adminUsername, environment.adminPassword);
  } catch (error) {
    // A failed sign-in must explain itself: where the browser ended up and which form fields it could see (no values).
    const fields = await page.locator('input, button').evaluateAll((elements) =>
      elements.map((element) => `${element.tagName.toLowerCase()}[id=${element.id} name=${element.getAttribute('name')} type=${element.getAttribute('type')}]`),
    );
    await page.screenshot({ path: 'test-results/global-setup-sign-in-failure.png' });
    console.error(`Global setup could not sign in. Page: ${page.url()}\nFields: ${fields.join(', ')}`);
    await browser.close();
    throw error;
  }
  await context.storageState({ path: '.auth/admin.json' });
  await browser.close();
}
