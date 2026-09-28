import '@angular/compiler';
import '@analogjs/vitest-angular/setup-snapshots';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { webcrypto } from 'node:crypto';

setupTestBed();

// jsdom's `crypto` implements getRandomValues but not `subtle` (real Android WebViews do) —
// PKCE's code_challenge needs SHA-256 via Web Crypto, so back it with Node's own implementation.
if (typeof globalThis.crypto.subtle === 'undefined') {
  Object.defineProperty(globalThis.crypto, 'subtle', { value: webcrypto.subtle });
}
