import { test as base } from '@playwright/test';
import { requiredEnvironment } from './environment';

const environment = requiredEnvironment();

/** The shared Playwright `test`; the runner stores the signed-in admin session as `.auth/admin.json` for every context. */
export const test = base;

export { expect } from '@playwright/test';
export { environment };
