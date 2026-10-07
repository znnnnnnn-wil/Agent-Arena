const { defineConfig } = require('@playwright/test');
const base = require('./playwright.config');

// Font and quality profiles are fixed by the visual fixtures; let Chromium select its renderer.
module.exports = defineConfig(base, {
  workers: 1,
  timeout: 60_000
});
