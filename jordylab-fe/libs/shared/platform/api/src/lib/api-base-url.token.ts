import { InjectionToken } from '@angular/core';

/**
 * Absolute API origin, used only when running natively (research D5) — supplied by the
 * `mobile` build configuration's `environment.mobile.ts`. Web builds never read this: the
 * interceptor and {@link resolveUrl} both no-op unless {@link PlatformService.isNative} is true.
 */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL');
