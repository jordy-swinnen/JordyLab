import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { assertPinnedWebView } from './src/support/preflight';
import { ensureReversedPorts } from './src/support/reverse-ports';
import { requiredEnvironment } from './src/support/environment';

process.env['APPIUM_HOME'] = join(process.cwd(), '.appium');
requiredEnvironment();

// Chrome on the emulator, for what an Android *browser* visitor sees (the install prompt). The same Appium server setup and WebView pin apply:
// Chrome and the WebView come from the same image.
export const config: WebdriverIO.Config = {
  runner: 'local',
  specs: ['./src/browser/**/*.e2e.ts'],
  maxInstances: 1,
  services: [
    [
      'appium',
      {
        command: 'appium',
        args: { address: '127.0.0.1', port: 4723, basePath: '/', allowInsecure: 'uiautomator2:chromedriver_autodownload' },
      },
    ],
  ],
  port: 4723,
  capabilities: [
    {
      platformName: 'Android',
      browserName: 'Chrome',
      'appium:automationName': 'UiAutomator2',
      'appium:noReset': false,
      'appium:newCommandTimeout': 300,
      'appium:chromeOptions': { args: ['--disable-fre', '--no-first-run', '--no-default-browser-check'] },
    },
  ],
  framework: 'mocha',
  // One retry per test: a software emulator occasionally stalls on a cold start; a real regression fails both attempts.
  mochaOpts: { timeout: 240_000, retries: 1 },
  reporters: ['spec'],
  logLevel: 'info',
  onPrepare: () => {
    assertPinnedWebView();
  },
  // Before every session's tests: the adb reverse tunnels can disappear when the adb server restarts (see reverse-ports.ts).
  before: () => {
    ensureReversedPorts();
  },
  // A failed test leaves a screenshot and the page source behind for the CI artifact.
  afterTest: async (test, _context, { passed }) => {
    if (passed) {
      return;
    }
    const name = test.title.replace(/[^a-z0-9]+/gi, '-').toLowerCase();
    await mkdir('logs', { recursive: true });
    await driver.saveScreenshot(`logs/${name}.png`);
    await writeFile(`logs/${name}.xml`, await driver.getPageSource());
  },
};
