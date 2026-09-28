import type { CapacitorConfig } from '@capacitor/cli';

// STOP-AND-REPORT GATE (research D14): `appId` is a placeholder. It must be a reverse-DNS id on
// the real production domain (feature 008) and, once the first release ships, can never change.
// Do not use this placeholder for a real release build.
const config: CapacitorConfig = {
  appId: 'dev.jordylab.mobile.placeholder',
  appName: 'JordyLab',
  webDir: '../../dist/apps/jordylab/browser',
  server: {
    androidScheme: 'https',
    hostname: 'localhost',
  },
};

export default config;
