// Native app build for the automated Android end-to-end runs (spec 012). Same shape as environment.mobile.ts, but every address is a
// fixed logical one that the emulator reaches through `adb reverse` (the runner starts Keycloak and the backend on exactly these
// ports for an Android run), and the App Link callback uses a test host the debug build declares (`-PjordylabAppLinkHost`).
export const environment = {
  production: false,
  keycloakUrl: 'http://localhost:18180',
  keycloakRealm: 'jordylab',
  keycloakClientId: 'jordylab-mobile',
  apiBaseUrl: 'http://localhost:18080',
  mobileCallbackUri: 'https://e2e.jordylab.test/mobile/callback',
};
