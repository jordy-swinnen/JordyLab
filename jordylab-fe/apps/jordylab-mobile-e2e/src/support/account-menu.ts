const ACCOUNT_MENU = '[data-testid="user-menu-trigger"]';

/**
 * Waits until a visible account menu is on the page. The shell renders the trigger more than once (one per layout, only one of them
 * shown), so the selector matches several elements and the check must look at all of them: WebdriverIO 10 no longer waits on "the first".
 */
export async function waitForAccountMenu(timeout: number): Promise<void> {
  await browser.waitUntil(
    async () => {
      for (const trigger of await $$(ACCOUNT_MENU)) {
        if (await trigger.isDisplayed()) {
          return true;
        }
      }

      return false;
    },
    { timeout, interval: 500, timeoutMsg: 'No visible account menu on the page' },
  );
}

export async function isAccountMenuDisplayed(): Promise<boolean> {
  for (const trigger of await $$(ACCOUNT_MENU)) {
    if (await trigger.isDisplayed()) {
      return true;
    }
  }

  return false;
}
