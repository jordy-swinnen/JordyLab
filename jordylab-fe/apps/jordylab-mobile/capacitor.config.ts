import type { CapacitorConfig } from '@capacitor/cli';

// Android application id: reverse-DNS of the production domain jordylab.be (research D14, decided
// 2026-09-30, spec 011 Q-06). It can never change once the first release ships.
const config: CapacitorConfig = {
  appId: 'be.jordylab.app',
  appName: 'JordyLab',
  webDir: '../../dist/apps/jordylab/browser',
  server: {
    androidScheme: 'https',
    hostname: 'localhost',
    // Only for the automated emulator tests (CAPACITOR_E2E=1, set by jordylab-fe/e2e): they talk to Keycloak and the backend
    // over plain HTTP on localhost through adb reverse. A normal sync never sets it, so real builds stay HTTPS-only.
    ...(process.env['CAPACITOR_E2E'] === '1' ? { cleartext: true } : {}),
  },
  ...(process.env['CAPACITOR_E2E'] === '1' ? { android: { allowMixedContent: true } } : {}),
};

export default config;
