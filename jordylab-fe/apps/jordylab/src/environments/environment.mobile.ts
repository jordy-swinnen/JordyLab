// STOP-AND-REPORT GATE (research D13): keycloakUrl/apiBaseUrl are placeholders blocked on
// feature 008 (production HTTPS deployment) — do not use these values for a real release build.
export const environment = {
  production: true,
  keycloakUrl: 'https://PRODUCTION_DOMAIN_PLACEHOLDER/auth',
  keycloakRealm: 'jordylab',
  keycloakClientId: 'jordylab-mobile',
  apiBaseUrl: 'https://PRODUCTION_DOMAIN_PLACEHOLDER',
  mobileCallbackUri: 'https://PRODUCTION_DOMAIN_PLACEHOLDER/mobile/callback',
};
