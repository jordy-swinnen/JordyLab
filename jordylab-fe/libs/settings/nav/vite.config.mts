import { resolve } from 'node:path';
/// <reference types='vitest' />
import { defineConfig } from 'vite';
import angular from '@analogjs/vite-plugin-angular';
import { nxViteTsPaths } from '@nx/vite/plugins/nx-tsconfig-paths.plugin';
import { nxCopyAssetsPlugin } from '@nx/vite/plugins/nx-copy-assets.plugin';

export default defineConfig(() => ({
  root: import.meta.dirname,
  cacheDir: '../../../node_modules/.vite/libs/settings/nav',
  plugins: [angular(), nxViteTsPaths(), nxCopyAssetsPlugin(['*.md'])],
  test: {
    name: 'settings-nav',
    watch: false,
    globals: true,
    passWithNoTests: true,
    environment: 'jsdom',
    include: ['{src,tests}/**/*.{test,spec}.{js,mjs,cjs,ts,mts,cts,jsx,tsx}'],
    setupFiles: ['src/test-setup.ts'],
    server: { deps: { inline: ['@ngneat/spectator'] } },
    reporters: ['default'],
    coverage: {
      reportsDirectory: resolve(
        import.meta.dirname,
        '../../../coverage/libs/settings/nav',
      ),
      provider: 'v8' as const,
      thresholds: { lines: 80 },
    },
  },
}));
