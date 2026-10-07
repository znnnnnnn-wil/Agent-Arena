const { test, expect } = require('@playwright/test');
const { installTuringMockApi } = require('./helpers/turing-mock-api');
const { prepareVisualPage } = require('./helpers/visual-env');

test.beforeEach(async ({ page }, testInfo) => {
  if (testInfo.title.includes('@visual')) await prepareVisualPage(page);
});

async function seedOwnerSession(page) {
  await page.addInitScript(() => {
    localStorage.setItem('agentArenaUser', JSON.stringify({
      userId: 1,
      nickname: '图灵验收用户',
      visitorToken: 'turing-test-token',
      account: false
    }));
    localStorage.setItem('agentArenaSession', JSON.stringify({ gameId: 77, mode: 'TURING_HUNT' }));
  });
}

async function openOwnerWatch(page) {
  await page.goto('/');
  await page.evaluate(() => goTo('watch'));
  await expect(page.locator('.turing-watch-dashboard')).toBeVisible();
  await page.evaluate(() => document.fonts.ready);
  await expect(page.locator('[data-arena-3d="turing"]')).toHaveAttribute('data-arena3d-assets', '6', { timeout: 15000 });
}

test('图灵潜伏局创建向导固定一名真人和五名 AI，并使用专属接口启动', async ({ page }) => {
  await installTuringMockApi(page, { emptyLobby: true, started: false });
  await page.goto('/#create');
  await page.locator('[data-mode="TURING_HUNT"]').click();
  await page.locator('#next-step').click();

  await expect(page.getByRole('heading', { name: '1 名真人潜入 5 名 AI' })).toBeVisible();
  await expect(page.getByText('固定 6 席 · 3 轮')).toBeVisible();
  await page.locator('#human-seat').selectOption('4');
  await page.locator('#next-step').click();

  const rows = page.locator('.seat-config-row');
  await expect(rows).toHaveCount(6);
  await expect(rows.nth(3).locator('.seat-model-select')).toBeDisabled();
  await expect(rows.nth(3).locator('.seat-persona-select')).toBeEnabled();
  for (const index of [0, 1, 2, 4, 5]) {
    await expect(rows.nth(index).locator('.seat-model-select option')).toHaveCount(2);
    await rows.nth(index).locator('.seat-model-select').selectOption('1');
  }
  await page.locator('#next-step').click();

  await expect(page.locator('.confirm-lineup article')).toHaveCount(6);
  await expect(page.locator('.human-seat-review')).toContainText('类型：真人');
  await expect(page.locator('.human-seat-review')).toContainText('真人直接操作');
  await expect(page.locator('.confirm-lineup')).toContainText('类型：AI');
  await page.locator('#game-name').fill('Playwright 图灵潜伏局');

  const createRequestPromise = page.waitForRequest((request) =>
    request.method() === 'POST' && request.url().endsWith('/api/games/turing-hunt'));
  const startRequestPromise = page.waitForRequest((request) =>
    request.method() === 'POST' && request.url().endsWith('/api/games/77/turing-start'));
  await page.locator('#next-step').click();
  const createRequest = await createRequestPromise;
  await startRequestPromise;
  const body = createRequest.postDataJSON();

  expect(body.humanSeatNo).toBe(4);
  expect(body.seatModelConfigIds).toEqual({ 1: 1, 2: 1, 3: 1, 5: 1, 6: 1 });
  expect(body.seatModelConfigIds).not.toHaveProperty('4');
  expect(Object.keys(body.seatAgentProfileIds)).toHaveLength(6);
  expect(body).not.toHaveProperty('playerId');
  expect(body).not.toHaveProperty('actorPlayerId');
});

test('房主真人席位可以提交秘密投票，客户端请求不含内部玩家 ID', async ({ page }) => {
  await installTuringMockApi(page);
  await seedOwnerSession(page);
  await openOwnerWatch(page);

  const form = page.locator('[data-turing-human-form]');
  await expect(form).toBeVisible();
  await expect(form).toHaveAttribute('data-action', 'VOTE');
  await expect(page.locator('.turing-current-stage').getByRole('heading', { name: '秘密投票', exact: true })).toBeVisible();
  await expect(page.locator('.turing-card-model')).toHaveCount(0);
  await expect(page.locator('.turing-watch-dashboard')).not.toContainText('DeepSeek 推理席');
  await form.locator('[name="targetSeatNo"]').selectOption('5');
  await form.locator('[name="reasonSummary"]').fill('太短');
  await form.getByRole('button', { name: '锁定并提交' }).click();
  await expect(page.locator('#toast')).toContainText('还需');
  await form.locator('[name="reasonSummary"]').fill('回答过于抽象，回避了具体顺序');

  const actionRequestPromise = page.waitForRequest((request) =>
    request.method() === 'POST' && request.url().endsWith('/api/games/77/turing-human-actions'));
  await form.getByRole('button', { name: '锁定并提交' }).click();
  const actionRequest = await actionRequestPromise;
  const body = actionRequest.postDataJSON();

  expect(body).toEqual({
    action: 'VOTE',
    targetSeatNo: 5,
    reasonSummary: '回答过于抽象，回避了具体顺序'
  });
  expect(body).not.toHaveProperty('playerId');
  expect(body).not.toHaveProperty('actorPlayerId');
  await expect(page.locator('#toast')).toContainText('你的行动已锁定');
  await expect(page.getByText('等待本阶段提交')).toBeVisible();
  await expect(page.locator('[data-turing-human-form]')).toHaveCount(0);
});

test('公开观战只有安全投影，不显示真人表单、本人标记或模型身份', async ({ page }) => {
  await installTuringMockApi(page);
  await page.goto('/#public-game=77');

  await expect(page.locator('.turing-watch-dashboard')).toBeVisible();
  await expect(page.getByText('公开只读观战', { exact: true }).first()).toBeVisible();
  await expect(page.locator('[data-turing-human-form]')).toHaveCount(0);
  await expect(page.locator('.turing-seat-card')).toHaveCount(6);
  expect(await page.locator('.turing-seat-card').evaluateAll((cards) => cards.every((card) => card.className === 'turing-seat-card'))).toBeTruthy();
  await expect(page.locator('.turing-watch-dashboard')).not.toContainText('你 · 真人');
  await expect(page.locator('.turing-watch-dashboard')).not.toContainText('DeepSeek 推理席');
  await expect(page.locator('.turing-watch-dashboard')).not.toContainText('真人是：');
});

test('终局使用独立身份结果视图，并在复盘页按标签组织信息', async ({ page }) => {
  await installTuringMockApi(page, { finished: true });
  await seedOwnerSession(page);
  await openOwnerWatch(page);

  const finish = page.locator('.turing-result-view');
  await expect(finish).toBeVisible();
  await expect(finish).toContainText('真人成功潜伏');
  await expect(finish.locator('.turing-result-seat')).toHaveCount(6);
  await expect(finish.locator('.turing-result-hero')).toContainText('3号');
  await expect(finish).toContainText('误淘汰 AI');
  await expect(finish).toContainText('DeepSeek 推理席');
  await expect(finish).toContainText('淘汰顺序');
  await expect(finish).toContainText('最先识破真人');

  await page.evaluate(() => goTo('replays'));
  await expect(page.locator('.turing-replay-tabs')).toBeVisible();
  await expect(page.locator('[data-turing-replay-pane="overview"]')).toContainText('DeepSeek 推理席');
  await expect(page.locator('[data-turing-replay-pane="overview"]')).toContainText('判断最准确 AI');
  await page.getByRole('button', { name: '逐轮过程' }).click();
  await expect(page.locator('.turing-replay-round')).toHaveCount(2);
  await page.getByRole('button', { name: '技术日志' }).click();
  await expect(page.locator('.turing-technical-log')).not.toHaveAttribute('open', '');
});

test('@visual 图灵潜伏局桌面公开题目快照', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name.includes('mobile'), 'desktop stage capture');
  await installTuringMockApi(page, { stage: 'prompt' });
  await seedOwnerSession(page);
  await openOwnerWatch(page);
  await page.locator('.topbar').evaluate((element) => { element.style.visibility = 'hidden'; });
  const layout = await page.evaluate(() => ({
    viewport: window.innerWidth,
    documentWidth: document.documentElement.scrollWidth,
    pageScrolls: document.documentElement.scrollHeight > window.innerHeight + 1,
    boardColumns: getComputedStyle(document.querySelector('.turing-stage-layout')).gridTemplateColumns.split(' ').length
  }));
  expect(layout.documentWidth).toBeLessThanOrEqual(layout.viewport + 1);
  expect(layout.pageScrolls).toBeFalsy();
  expect(layout.boardColumns).toBe(2);
  await expect(page.locator('.turing-watch-dashboard')).toHaveScreenshot('turing-public-prompt.png');
});

for (const [stage, title, screenshot, selector] of [
  ['answer', '匿名回答', 'turing-answer-reveal.png', '.turing-answer-grid'],
  ['qna', '质询回应', 'turing-qna-stage.png', '.turing-conversation'],
  ['voteReveal', '投票揭示', 'turing-vote-reveal.png', '.turing-vote-reveal'],
  ['identity', '身份揭示', 'turing-identity-reveal.png', '.turing-result-view']
]) {
  test(`@visual 图灵潜伏局桌面${title}快照`, async ({ page }, testInfo) => {
    test.skip(testInfo.project.name.includes('mobile'), 'desktop stage capture');
    await installTuringMockApi(page, { stage });
    await seedOwnerSession(page);
    await openOwnerWatch(page);
    await page.locator('.topbar').evaluate((element) => { element.style.visibility = 'hidden'; });
    await expect(page.locator(selector)).toBeVisible();
    await expect(page.locator('.turing-watch-dashboard')).toHaveScreenshot(screenshot);
  });
}

test('@visual 图灵潜伏局移动端真人输入快照', async ({ page }, testInfo) => {
  test.skip(!testInfo.project.name.includes('mobile'), 'mobile input capture');
  await installTuringMockApi(page, { stage: 'prompt' });
  await seedOwnerSession(page);
  await openOwnerWatch(page);
  await page.locator('.topbar').evaluate((element) => { element.style.visibility = 'hidden'; });
  const form = page.locator('[data-turing-human-form]');
  await expect(form).toBeVisible();
  await form.locator('[name="answer"]').fill('我会先确认身边的人是否安全，因为这比判断停电范围更紧急，也方便安排下一步行动。');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBeTruthy();
  await expect(form.getByRole('button', { name: '锁定并提交' })).toBeInViewport();
  await expect(page.locator('.turing-watch-dashboard')).toHaveScreenshot('turing-mobile-human-input.png');
});
