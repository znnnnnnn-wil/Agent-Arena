const fs = require('node:fs/promises');
const path = require('node:path');
const { chromium } = require('@playwright/test');
const { startStaticServer } = require('./static-server.cjs');

// Optional shareable video; generated artifacts stay under ignored target/.
async function recordDemo() {
  const output = path.resolve(__dirname, '../target/demo');
  await fs.mkdir(output, { recursive: true });
  const server = await startStaticServer(4174);
  console.log('Local: http://127.0.0.1:4174/demo.html');
  let browser;
  try {
    browser = await chromium.launch();
    const context = await browser.newContext({ viewport: { width: 1440, height: 1120 },
      reducedMotion: 'no-preference', recordVideo: { dir: output, size: { width: 1440, height: 1120 } } });
    const page = await context.newPage();
    await page.goto('http://127.0.0.1:4174/demo.html');
    await page.locator('#demo-play').waitFor({ state: 'visible' });
    await page.waitForFunction(() => !document.querySelector('#demo-play').disabled
      && document.querySelector('#demo-arena')?.dataset.arena3dAssets === '6');
    await page.evaluate(() => document.fonts.ready);
    if (await page.locator('#demo-play').getAttribute('aria-pressed') === 'true') await page.locator('#demo-play').click();
    await page.locator('#demo-progress').evaluate(input => { input.value = '0'; input.dispatchEvent(new Event('input')); });
    await page.screenshot({ path: path.join(output, 'desktop.png'), fullPage: true });
    await page.locator('#demo-play').click();
    await page.waitForFunction(() => Number(document.querySelector('#demo-progress').value) >= 60, null, { timeout: 90_000 });
    const videoPath = await page.video().path();
    await context.close();
    const filename = path.join(output, `Agent-Arena-60s-${Date.now()}.webm`);
    await fs.rename(videoPath, filename);
    console.log(`Video: ${filename}`);
    const mobile = await browser.newPage({ viewport: { width: 412, height: 915 }, reducedMotion: 'reduce' });
    await mobile.goto('http://127.0.0.1:4174/demo.html#t=8');
    await mobile.waitForFunction(() => document.querySelector('#demo-arena')?.dataset.arena3dAssets === '6');
    await mobile.screenshot({ path: path.join(output, 'mobile.png'), fullPage: true });
    await mobile.goto('http://127.0.0.1:4174/');
    await mobile.screenshot({ path: path.join(output, 'home-mobile.png'), fullPage: true });
    console.log(`Screenshots: ${output}`);
  } finally {
    await browser?.close();
    await new Promise((resolve) => server.close(resolve));
  }
}

recordDemo().catch((error) => { console.error(error); process.exitCode = 1; });
