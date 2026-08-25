const { defineConfig, devices } = require('@playwright/test');

const browserChannel = process.env.PLAYWRIGHT_CHANNEL;
const channel = browserChannel ? { channel: browserChannel } : {};

module.exports = defineConfig({
  testDir: './e2e',
  globalSetup: require.resolve('./e2e/global-setup.cjs'),
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : 4,
  reporter: process.env.CI ? [['html', { open: 'never' }], ['list']] : 'list',
  timeout: 30_000,
  expect: {
    timeout: 6_000,
    toHaveScreenshot: {
      animations: 'disabled',
      caret: 'hide',
      maxDiffPixelRatio: 0.02
    }
  },
  use: {
    baseURL: 'http://127.0.0.1:4173',
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    colorScheme: 'dark',
    reducedMotion: 'reduce',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  snapshotPathTemplate: '{testDir}/__screenshots__/{projectName}/{arg}{ext}',
  projects: [
    {
      name: 'desktop-chromium',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1440, height: 1000 },
        ...channel
      }
    },
    {
      name: 'mobile-chromium',
      use: {
        ...devices['Pixel 7'],
        viewport: { width: 412, height: 915 },
        ...channel
      }
    }
  ]
});
