import type { Page } from '@playwright/test';
import { expect } from './fixtures';

/**
 * Sets the account-wide theme (spec 024) through the API, for tests that need a specific look. The browser's stored value
 * only covers the first paint; the store then follows the account, so the account is what makes a theme stick.
 */
export async function setAccountTheme(page: Page, theme: string): Promise<void> {
  await page.request.get('/api/identity/auth/csrf');
  const cookies = await page.context().cookies();
  const xsrf = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? '';
  const response = await page.request.put('/api/identity/me/preferences', {
    headers: { 'X-XSRF-TOKEN': xsrf },
    data: { theme },
  });

  expect(response.status()).toBe(200);
}
