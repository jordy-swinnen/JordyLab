// Build for the automated end-to-end runs (spec 012). Same shape as environment.ts, but the Keycloak address is not known at
// build time: each run starts its own Keycloak on a free port, so the Playwright setup injects it before the app boots
// (`window.__JORDYLAB_E2E__ = { keycloakUrl }`). The fallback is the fixed logical address the Android build uses, where adb
// reverse maps localhost:18180 on the emulator to the real port on the host.
interface E2eRuntimeConfig {
  readonly keycloakUrl?: string;
}

const injected = (globalThis as { __JORDYLAB_E2E__?: E2eRuntimeConfig }).__JORDYLAB_E2E__;

export const environment = {
  production: false,
  keycloakUrl: injected?.keycloakUrl ?? 'http://localhost:18180',
  keycloakRealm: 'jordylab',
  keycloakClientId: 'jordylab-host',
};
