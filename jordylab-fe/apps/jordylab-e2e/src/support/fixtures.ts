import { test as base } from '@playwright/test';
import { requiredEnvironment } from './environment';
import { pointAppAtKeycloak } from './session';

const environment = requiredEnvironment();

/** `test` whose every browser context already knows this run's Keycloak address. Signed in as the admin by default. */
export const test = base.extend({
  context: async ({ context }, use) => {
    await pointAppAtKeycloak(context, environment.keycloakUrl);
    await use(context);
  },
});

export { expect } from '@playwright/test';
export { environment };
