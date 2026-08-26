const { test, expect } = require('@playwright/test');
const { installMockApi } = require('./helpers/mock-api');

test('游客可以组合模型与人格创建并启动狼人杀', async ({ page }) => {
  await installMockApi(page, { emptyLobby: true, started: false });
  await page.goto('/');
  await page.getByRole('button', { name: /创建我的世界|创建第一场对局/ }).first().click();

  await page.getByRole('button', { name: /下一步：配置规则/ }).click();
  await page.getByRole('button', { name: /下一步：选择智能体/ }).click();
  await expect(page.getByRole('heading', { name: '组合模型与人格' })).toBeVisible();
  const defaultPersonaIds = await page.locator('.seat-persona-select').evaluateAll((selects) => selects.map((select) => select.value));
  expect(new Set(defaultPersonaIds).size).toBe(8);

  await page.locator('.seat-model-select').first().selectOption('1');
  await page.locator('.seat-persona-select').first().selectOption('7');
  await page.getByRole('button', { name: /下一步：确认并开始/ }).click();
  await expect(page.locator('.confirm-lineup')).toContainText('人格：激进领袖');
  await page.locator('#game-name').fill('Playwright 人格实验局');
  await page.locator('#next-step').click();

  await expect(page.locator('#watch-page')).toHaveClass(/active/);
  await expect(page.getByRole('heading', { name: /人格辨识度实验局/ })).toBeVisible();
  const mobileCommentaryTab = page.locator('[data-mobile-arena="report"]');
  if (await mobileCommentaryTab.isVisible()) await mobileCommentaryTab.click();
  await expect(page.getByText('赛事内容')).toBeVisible();
  await expect(page.getByText('服务端会生成结构化赛事解说')).toHaveCount(0);
});

test('狼人杀观众标记只写入当前浏览器', async ({ page }) => {
  await installMockApi(page);
  await page.addInitScript(() => {
    localStorage.setItem('agentArenaUser', JSON.stringify({ userId: 1, nickname: '观众', visitorToken: 'test-token' }));
  });
  await page.goto('/#clip=42:g42-s41-question');
  await page.locator('[data-arena-player="101"]').evaluate((element) => element.click());
  await expect(page.locator('.arena-player-drawer')).toBeVisible();
  await page.locator('[data-audience-action="suspect"]').click();
  await page.locator('[data-audience-action="follow"]').click();
  await page.locator('[data-audience-action="predict"]').click();

  const audience = await page.evaluate(() => JSON.parse(localStorage.getItem('agentArenaAudience:42')));
  expect(audience.suspectedWolfSeats).toEqual([1]);
  expect(audience.followedSeats).toEqual([1]);
  expect(audience.eliminationPredictionSeatNo).toBe(1);
  expect(audience.predictionRound).toBe(2);
});

test('模型中心可以创建轻量 Agent 人格', async ({ page }) => {
  await installMockApi(page);
  await page.goto('/#models');
  await expect(page.getByRole('heading', { name: '轻量 Agent 人格' })).toBeVisible();
  await page.locator('#agent-profile-name').fill('谨慎侦探');
  await page.locator('#agent-profile-traits').fill('谨慎, 观察');
  await page.locator('#agent-profile-style').fill('先列证据，再给判断');
  await page.getByRole('button', { name: '保存 Agent 人格' }).click();
  await expect(page.locator('#toast')).toContainText('Agent 人格已保存');
});

test('房主可以保存阵容并用相同配置发起重赛', async ({ page }) => {
  await installMockApi(page, { finished: true });
  await page.addInitScript(() => {
    localStorage.setItem('agentArenaUser', JSON.stringify({
      userId: 1,
      nickname: '端到端测试用户',
      visitorToken: 'test-token',
      account: false
    }));
    localStorage.setItem('agentArenaSession', JSON.stringify({ gameId: 42, mode: 'WEREWOLF' }));
  });
  await page.goto('/');
  await page.evaluate(() => goTo('watch'));

  await expect(page.locator('.real-watch-header h1 small')).toHaveText('#42');
  await expect(page.locator('.rematch-game')).toBeVisible();

  page.once('dialog', (dialog) => dialog.accept('冠军阵容'));
  const templateRequest = page.waitForRequest((request) =>
    request.method() === 'POST' && request.url().endsWith('/api/lineup-templates/from-game/42'));
  await page.locator('.save-lineup-template').click();
  await templateRequest;
  await expect(page.locator('#toast')).toContainText('阵容模板已保存');

  const rematchRequest = page.waitForRequest((request) =>
    request.method() === 'POST' && request.url().endsWith('/api/games/42/rematch'));
  await page.locator('.rematch-game').click();
  await rematchRequest;

  await expect.poll(() => page.evaluate(() => JSON.parse(localStorage.getItem('agentArenaSession')).gameId)).toBe(43);
  await expect(page.locator('.real-watch-header h1 small')).toHaveText('#43');
  await expect(page.locator('.real-auto')).toBeVisible();
});
