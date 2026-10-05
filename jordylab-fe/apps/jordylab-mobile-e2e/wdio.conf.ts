import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { requiredEnvironment } from './src/support/environment';
import { assertPinnedWebView } from './src/support/preflight';

// Appium keeps its drivers in a directory inside this project (installed by `bun run setup`), not in the developer's home.
process.env['APPIUM_HOME'] = join(process.cwd(), '.appium');

const environment = requiredEnvironment();

export const config: WebdriverIO.Config = {
  runner: 'local',
  specs: ['./src/specs/**/*.e2e.ts'],
  maxInstances: 1,
  // Appium resolves the matching chromedriver for the WebView it finds; the preflight below pins which WebView that may be.
  services: [
    [
      'appium',
      {
        command: 'appium',
        args: { address: '127.0.0.1', port: 4723, basePath: '/', allowInsecure: ['uiautomator2:chromedriver_autodownload'] },
      },
    ],
  ],
  port: 4723,
  capabilities: [
    {
      platformName: 'Android',
      'appium:automationName': 'UiAutomator2',
      'appium:app': environment.apkFirst,
      'appium:appPackage': environment.androidPackage,
      'appium:autoGrantPermissions': true,
      'appium:noReset': false,
      'appium:newCommandTimeout': 300,
      'appium:ensureWebviewsHavePages': true,
    },
  ],
  framework: 'mocha',
  mochaOpts: { timeout: 240_000 },
  reporters: ['spec'],
  logLevel: 'info',
  onPrepare: () => {
    assertPinnedWebView();
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
