const { test, expect } = require('@playwright/test');
const { installMockApi } = require('./helpers/mock-api');
const { prepareVisualPage } = require('./helpers/visual-env');

test.beforeEach(async ({ page }, testInfo) => {
  await prepareVisualPage(page);
  await installMockApi(page, { voteStage: testInfo.title.includes('投票关系') });
  await page.addInitScript(() => {
    localStorage.setItem('agentArenaUser', JSON.stringify({ userId: 1, nickname: '视觉测试用户', visitorToken: 'test-token' }));
  });
});

async function openQuestionStage(page) {
  await page.goto('/#clip=42:g42-s41-question');
  await page.evaluate(() => document.fonts.ready);
  await expect(page.locator('.real-watch-dashboard')).toBeVisible();
  await expect(page.locator('.werewolf-arena-v2')).toBeVisible();
  await expect(page.locator('[data-arena-3d="werewolf"]')).toHaveAttribute('data-arena3d-assets', '8', { timeout: 15000 });
  await expect(page.locator('.arena-player-column')).toHaveCount(0);
  await page.locator('.stream-state').evaluate((element) => { element.style.visibility = 'hidden'; });
  await page.locator('.topbar').evaluate((element) => { element.style.visibility = 'hidden'; });
}

test('@visual 狼人杀2.0质询主舞台桌面与移动端快照', async ({ page }, testInfo) => {
  await openQuestionStage(page);
  if (testInfo.project.name.includes('mobile')) {
    await page.locator('[data-mobile-arena="arena"]').click();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBeTruthy();
  } else {
    const layout = await page.evaluate(() => ({
      pageScrolls: document.documentElement.scrollHeight > window.innerHeight + 1,
      arenaScrolls: document.querySelector('.werewolf-arena-v2').scrollHeight > document.querySelector('.werewolf-arena-v2').clientHeight + 1,
      columns: getComputedStyle(document.querySelector('.werewolf-watch-grid')).gridTemplateColumns.split(' ').length
    }));
    expect(layout).toEqual({ pageScrolls: false, arenaScrolls: false, columns: 2 });
  }
  await page.evaluate(() => window.scrollTo(0, 0));
  await expect(page.locator('.real-watch-dashboard')).toHaveScreenshot('werewolf-question-stage.png');
});

test('@visual 狼人杀2.0玩家详情抽屉快照', async ({ page }) => {
  await openQuestionStage(page);
  await page.locator('.arena3d-label[data-arena3d-player="103"]').click();
  await expect(page.locator('.arena-player-drawer')).toBeVisible();
  await expect(page.locator('.arena-player-drawer')).toContainText('逻辑侦探');
  await expect(page.locator('.arena-player-drawer')).not.toContainText('预言家');
  await expect(page.locator('.arena-player-drawer')).toHaveScreenshot('werewolf-player-drawer.png');
});

test('@visual 狼人杀2.0投票关系视图快照', async ({ page }) => {
  await openQuestionStage(page);
  await page.locator('[data-wolf-relation="VOTE"]').click();
  await expect(page.locator('.wolf-links .relation-vote')).toHaveCount(2);
  await expect(page.locator('.werewolf-arena-v2')).toHaveScreenshot('werewolf-vote-relations.png');
});

test('@visual Agent 人格中心视觉快照', async ({ page }) => {
  await page.goto('/#models');
  await page.evaluate(() => document.fonts.ready);
  await expect(page.locator('#agent-studio')).toBeVisible();
  await page.locator('.topbar').evaluate((element) => { element.style.visibility = 'hidden'; });
  await page.evaluate(() => window.scrollTo(0, 0));
  await expect(page.locator('#agent-studio')).toHaveScreenshot('agent-studio.png');
});
