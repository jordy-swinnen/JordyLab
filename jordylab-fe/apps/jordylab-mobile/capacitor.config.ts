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
  },
};

export default config;
