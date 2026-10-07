const { defineConfig } = require('@playwright/test');
const base = require('./playwright.config');

// Use the same software renderer on workstations and hosted Windows runners.
module.exports = defineConfig(base, {
  workers: 1,
  timeout: 60_000,
  use: {
    launchOptions: {
      args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader']
    }
  }
});
