import { environment, expect, test } from './support/fixtures';
import { signedInApprovedGuest } from './support/guest';
import { pointAppAtKeycloak } from './support/session';

/**
 * The installable-web-app shell (spec 017 US1, FR-001/FR-002). These journeys prove what the browser install criteria
 * depend on: a valid manifest served with its registered media type, and a registered service worker.
 */
test.describe('Installable web app', () => {
  test('the manifest is served with every installability field', async ({ page }) => {
    const response = await page.request.get('/manifest.webmanifest');

    expect(response.status()).toBe(200);
    expect(response.headers()['content-type']).toContain('application/manifest+json');

    const manifest = (await response.json()) as {
      name?: string;
      short_name?: string;
      id?: string;
      start_url?: string;
      scope?: string;
      display?: string;
      background_color?: string;
      theme_color?: string;
      icons?: { src: string; sizes: string; type: string; purpose?: string }[];
      share_target?: {
        action?: string;
        method?: string;
        params?: Record<string, string>;
      };
    };

    expect(manifest.name).toBe('JordyLab');
    expect(manifest.short_name).toBe('JordyLab');
    expect(manifest.id).toBe('/');
    expect(manifest.start_url).toBe('/');
    expect(manifest.scope).toBe('/');
    expect(manifest.display).toBe('standalone');
    expect(manifest.background_color).toBe('#131314');
    expect(manifest.theme_color).toBe('#131314');

    const iconSizes = (manifest.icons ?? []).map((icon) => icon.sizes);
    expect(iconSizes).toEqual(expect.arrayContaining(['192x192', '512x512']));
    for (const icon of manifest.icons ?? []) {
      expect(icon.type).toBe('image/png');
      expect(icon.purpose).toContain('maskable');
    }

    // The Web Share Target an installed Android app appears in the share sheet with (spec 017 US2, FR-007).
    expect(manifest.share_target).toEqual({
      action: '/share',
      method: 'GET',
      params: { title: 'title', text: 'text', url: 'url' },
    });
  });

  test('the service worker is served and registers', async ({ page }) => {
    const response = await page.request.get('/sw.js');

    expect(response.status()).toBe(200);
    expect(response.headers()['content-type']).toContain('javascript');

    await page.goto('/');
    await expect
      .poll(() => page.evaluate(async () => Boolean(await navigator.serviceWorker.getRegistration())))
      .toBe(true);
  });
});

/**
 * The Web Share Target landing screen (spec 017 US2, contracts/share-target-contract.md). The OS share sheet arrives as
 * a navigation with ?title&text&url, exactly as simulated here.
 */
test.describe('Share target', () => {
  test('a guest sees only Ask LibBot and lands in LibBot with the shared text prefilled', async ({ browser, page }) => {
    const guest = await signedInApprovedGuest(browser, page, environment);
    try {
      await guest.page.goto(
        '/share?text=Cozy%20games%20for%20four&url=https%3A%2F%2Fexample.test%2Flist',
      );

      await expect(guest.page.getByRole('heading', { level: 2, name: 'Share to JordyLab' })).toBeVisible();
      await expect(guest.page.locator('.jordylab-share-landing__text')).toContainText('Cozy games for four');
      await expect(guest.page.getByRole('button', { name: 'Save to FNA' })).toHaveCount(0);

      await guest.page.getByRole('button', { name: 'Ask LibBot' }).click();

      await expect(guest.page).toHaveURL(/\/games\/libbot\?prefill=Cozy/);
    } finally {
      await guest.context.close();
    }
  });

  test('an admin saves the shared link to FNA', async ({ page }) => {
    const articleUrl = `https://example.test/shared-${Date.now()}`;
    await page.goto(`/share?text=${encodeURIComponent(`Worth a read ${articleUrl}`)}`);

    await expect(page.getByRole('heading', { level: 2, name: 'Share to JordyLab' })).toBeVisible();
    const saved = page.waitForResponse('**/api/fna/articles/manual');
    await page.getByRole('button', { name: 'Save to FNA' }).click();
    const response = await saved;

    expect(response.status()).toBe(201);
    expect((response.request().postDataJSON() as { url: string }).url).toBe(articleUrl);
    await expect(page).toHaveURL(/\/fna\/articles/);
  });

  test('a link shared inside the text only is still extracted for Save to FNA', async ({ page }) => {
    await page.goto('/share?text=Look%20at%20https%3A%2F%2Fexample.test%2Finside-text.');

    const saved = page.waitForResponse('**/api/fna/articles/manual');
    await page.getByRole('button', { name: 'Save to FNA' }).click();
    const response = await saved;

    expect(response.status()).toBe(201);
    expect((response.request().postDataJSON() as { url: string }).url).toBe('https://example.test/inside-text');
  });

  test('a shared link survives the sign-in round trip', async ({ browser }) => {
    // `storageState: undefined` opts out of the admin session the runner applies to hand-made contexts.
      const anonymous = await browser.newContext({ baseURL: environment.baseUrl, storageState: undefined });
    try {
      await pointAppAtKeycloak(anonymous, environment.keycloakUrl);
      const signInPage = await anonymous.newPage();
      await signInPage.goto('/share?text=remember%20me');

      // authGuard carries the share URL as returnUrl, so the login page is shown first. Wait for that
      // client-side redirect to settle before clicking; clicking straight after goto races the router.
      await expect(signInPage).toHaveURL(/\/login\?returnUrl=/);
      await expect(signInPage.getByRole('button', { name: 'Sign in with Keycloak' })).toBeVisible();
      await signInPage.getByRole('button', { name: 'Sign in with Keycloak' }).click();
      await signInPage.locator('#username').fill(environment.adminUsername);
      await signInPage.locator('#password').fill(environment.adminPassword);
      await signInPage.locator('#kc-login').click();

      await expect(signInPage.getByRole('heading', { level: 2, name: 'Share to JordyLab' })).toBeVisible();
      await expect(signInPage.locator('.jordylab-share-landing__text')).toContainText('remember me');
    } finally {
      await anonymous.close();
    }
  });

  test('opening the share screen with nothing shared shows a calm empty state', async ({ page }) => {
    await page.goto('/share');

    await expect(page.getByRole('heading', { level: 2, name: 'Nothing was shared' })).toBeVisible();
    await page.getByRole('link', { name: 'Open the Library' }).click();

    await expect(page).toHaveURL(/\/games\/grid/);
  });

  test('an old notification tap still lands on the Users screen', async ({ page }) => {
    await page.goto('/mobile/open?screen=settings-users');

    await expect(page).toHaveURL(/\/settings\/users/);
    await expect(page.getByRole('heading', { level: 2, name: 'Users' })).toBeVisible();
  });
});
