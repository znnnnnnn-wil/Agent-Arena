const { test, expect } = require('@playwright/test');

async function seek(page, seconds) {
  await page.locator('#demo-progress').evaluate((input, value) => {
    input.value = String(value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }, seconds);
}

test('无后端演示可播放、拖动、跳转与分享，且不改变真实会话', async ({ page }) => {
  test.setTimeout(60_000);
  const errors = [];
  const apiRequests = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.route('**/api/**', (route) => { apiRequests.push(route.request().url()); return route.abort(); });
  await page.addInitScript(() => {
    localStorage.setItem('agentArenaSession', JSON.stringify({ gameId: 123, mode: 'WEREWOLF' }));
    Object.defineProperty(navigator, 'clipboard', { value: { writeText: async (text) => { window.copiedDemoUrl = text; } } });
  });
  await page.goto('/demo.html');
  await expect(page.getByRole('heading', { level: 1 })).toContainText('看它们如何选择');
  await expect(page.getByRole('button', { name: '播放演示', exact: true })).toBeEnabled();
  await expect(page.locator('#demo-arena canvas')).toBeVisible({ timeout: 15000 });
  await expect(page.locator('#demo-arena')).toHaveAttribute('data-arena3d-assets', '6', { timeout: 20000 });
  await page.locator('#demo-play').click();
  await expect.poll(() => page.locator('#demo-progress').inputValue()).not.toBe('0');
  await page.locator('#demo-play').click();
  const paused = await page.locator('#demo-progress').inputValue();
  await page.waitForTimeout(300);
  expect(await page.locator('#demo-progress').inputValue()).toBe(paused);
  await seek(page, 8);
  await expect(page.locator('#demo-quote')).toContainText('救援P2以维持联盟并增加生存机会');
  await seek(page, 14);
  await expect(page.locator('#demo-result')).toContainText('救援与关系变化');
  await page.locator('[data-demo-chapter="1"]').click();
  await expect(page.locator('#demo-time')).toHaveText('00:20 / 01:00');
  await seek(page, 28);
  await expect(page.locator('#demo-action')).toHaveText('GUARD / 增强防御');
  await page.locator('#demo-speed').selectOption('2');
  await page.locator('#demo-share').click();
  expect(await page.evaluate(() => window.copiedDemoUrl)).toContain('/demo.html#t=28');
  await page.locator('[data-demo-chapter="2"]').click();
  await seek(page, 48);
  await expect(page.locator('#demo-quote')).toContainText('同时履行对P2的承诺');
  await seek(page, 60);
  await expect(page.locator('#demo-complete')).toBeVisible();
  await expect(page.locator('#demo-play')).toHaveAccessibleName('重新播放演示');
  await page.locator('#demo-play').click();
  await expect(page.locator('#demo-complete')).toBeHidden();
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('agentArenaSession')).gameId)).toBe(123);
  expect(apiRequests).toEqual([]);
  expect(errors).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('深链接、无 WebGL 地图和剪贴板降级均可用', async ({ page }) => {
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.addInitScript(() => {
    const original = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (type, ...args) {
      if (type.startsWith('webgl')) return null;
      return original.call(this, type, ...args);
    };
    Object.defineProperty(navigator, 'clipboard', { value: undefined });
  });
  await page.goto('/demo.html#t=28');
  await expect(page.locator('#demo-action')).toHaveText('GUARD / 增强防御');
  await expect(page.locator('.demo-map-fallback')).toBeVisible();
  await expect(page.locator('#demo-arena')).toHaveClass(/arena3d-unavailable/);
  await page.locator('#demo-share').click();
  await expect(page.locator('#demo-share-url')).toHaveValue(/demo.html#t=28$/);
  await page.locator('#demo-restart').click();
  await expect(page.locator('#demo-play')).toHaveAccessibleName('暂停演示');
  expect(errors).toEqual([]);
});

test('林地舞台在回退和切换片段时复用画布，并保留无医疗包时的倒地结果', async ({ page }) => {
  await page.goto('/demo.html#t=34');
  const host = page.locator('#demo-arena');
  await expect(host).toHaveAttribute('data-arena3d-map-style', 'forest-survival');
  await expect(host.getByRole('button', { name: /P2 · 倒地待援/ })).toBeVisible();
  await host.locator('canvas').evaluate(canvas => { canvas.dataset.reuseProbe = 'retained'; });
  await seek(page, 14);
  await expect(host.getByRole('button', { name: /P2 · HP 30/ })).toBeVisible();
  await seek(page, 34);
  await expect(host.getByRole('button', { name: /P2 · 倒地待援/ })).toBeVisible();
  await seek(page, 54);
  await expect(host.getByRole('button', { name: /P2 · HP 80/ })).toBeVisible();
  await expect(host.locator('canvas')).toHaveAttribute('data-reuse-probe', 'retained');
  await expect(host.locator('canvas')).toHaveCount(1);
  await host.getByRole('button', { name: '重置 3D 镜头' }).click();
  await expect(host.locator('canvas')).toBeVisible();
});

test('数据加载失败有明确提示，首页入口仍可离线访问', async ({ page }) => {
  await page.route('**/assets/demo/decisions.json', (route) => route.fulfill({ status: 503, body: 'Unavailable' }));
  await page.goto('/demo.html');
  await expect(page.locator('#demo-quote')).toContainText('演示数据加载失败');
  await expect(page.locator('#demo-play')).toBeDisabled();
  await page.route('**/api/**', (route) => route.abort());
  await page.goto('/');
  await expect(page.getByRole('link', { name: '▶ 观看 60 秒演示', exact: true })).toHaveAttribute('href', '/demo.html?autoplay=1');
  await expect(page.locator('.hero-demo-link img')).toBeVisible();
});
