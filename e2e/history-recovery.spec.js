const { test, expect } = require('@playwright/test');
const { installMockApi, EVENTS } = require('./helpers/mock-api');
const { installTuringMockApi } = require('./helpers/turing-mock-api');

const success = (data) => ({ code: '00000', msg: '成功', data });

async function controlledStream(page) {
  await page.addInitScript(() => {
    window.testStreams = [];
    window.EventSource = class {
      constructor() { window.testStreams.push(this); }
      addEventListener() {}
      close() {}
    };
  });
}

test('无状态变化的实时重连保留真人投票输入和表单节点', async ({ page }) => {
  await installTuringMockApi(page);
  await controlledStream(page);
  await page.addInitScript(() => {
    localStorage.setItem('agentArenaUser', JSON.stringify({ userId: 1, visitorToken: 'test-token' }));
    localStorage.setItem('agentArenaSession', JSON.stringify({ gameId: 77, mode: 'TURING_HUNT' }));
  });
  await page.goto('/');
  await page.evaluate(() => goTo('watch'));
  const form = page.locator('[data-turing-human-form]');
  await expect(form).toBeVisible();
  await form.locator('[name="targetSeatNo"]').selectOption('5');
  await form.locator('[name="reasonSummary"]').fill('回答过于抽象，回避了具体顺序');
  await form.evaluate(element => { element.dataset.reconnectProbe = 'preserved'; });
  await page.evaluate(async () => {
    window.testStreams[0].onopen();
    await watchLoadPromise;
  });
  await expect(form).toHaveAttribute('data-reconnect-probe', 'preserved');
  await expect(form.locator('[name="targetSeatNo"]')).toHaveValue('5');
  await expect(form.locator('[name="reasonSummary"]')).toHaveValue('回答过于抽象，回避了具体顺序');
});

test('长对局观战与复盘逐页读取完整事件', async ({ page }) => {
  await installMockApi(page);
  await controlledStream(page);
  const history = Array.from({ length: 1001 }, (_, index) => ({
    ...EVENTS[0], sequenceNo: index + 1,
    payload: { ...EVENTS[0].payload, publicSpeech: `历史发言 ${index + 1}` }
  }));
  const cursors = [];
  await page.route('**/api/public/games/42/replay?*', async (route) => {
    const url = new URL(route.request().url());
    const cursor = Number(url.searchParams.get('afterSequence') || 0);
    cursors.push(cursor);
    await route.fulfill({ json: success(history.filter((event) => event.sequenceNo > cursor).slice(0, 500)) });
  });

  await page.goto('/#clip=42:g42-s41-question');
  await expect.poll(() => page.evaluate(() => currentRealEvents.length)).toBe(1001);
  expect(cursors.slice(0, 3)).toEqual([0, 500, 1000]);
  expect(await page.evaluate(() => currentRealEvents[0].sequenceNo)).toBe(1);
  cursors.length = 0;
  await page.evaluate(() => goTo('replays'));
  await expect(page.locator('.replay-timeline')).toBeVisible();
  expect(cursors).toEqual([0, 500, 1000]);
});

test('实时流重连后同步断线期间的最终结算', async ({ page }) => {
  await installMockApi(page);
  await controlledStream(page);
  let finished = false;
  await page.route('**/api/public/games/42', async (route) => {
    await route.fulfill({ json: success({
      id: 42, name: '重连验收局', mode: 'WEREWOLF', publicGame: true,
      status: finished ? 'FINISHED' : 'RUNNING', currentPhase: finished ? 'FINISHED' : 'DAY_RESPONSE',
      currentRound: 2, maxPlayers: 8
    }) });
  });

  await page.goto('/#clip=42:g42-s41-question');
  await expect.poll(() => page.evaluate(() => lastRealGame?.status)).toBe('RUNNING');
  finished = true;
  // No game-event is delivered: the stream only reopens after the game has finished.
  await page.evaluate(() => window.testStreams[0].onopen());
  await expect.poll(() => page.evaluate(() => lastRealGame?.status)).toBe('FINISHED');
});

test('加载更多后仍可打开第20场以后的旧对局', async ({ page }) => {
  await installMockApi(page);
  const games = Array.from({ length: 25 }, (_, index) => ({
    id: index + 1, name: `分页对局 ${index + 1}`, mode: 'WEREWOLF', status: 'FINISHED',
    currentPhase: 'FINISHED', currentRound: 4, maxPlayers: 8, publicGame: true
  }));
  await page.route('**/api/games?*', async (route) => {
    const url = new URL(route.request().url());
    const pageNumber = Number(url.searchParams.get('page') || 0);
    const size = Number(url.searchParams.get('size') || 20);
    await route.fulfill({ json: success(games.slice(pageNumber * size, (pageNumber + 1) * size)) });
  });

  await page.goto('/');
  await expect(page.locator('.real-lobby-card')).toHaveCount(20);
  await page.getByRole('button', { name: '加载更多对局' }).click();
  await expect(page.locator('.real-lobby-card')).toHaveCount(25);
  await expect(page.getByRole('button', { name: '加载更多对局' })).toBeHidden();
  await page.locator('.real-lobby-card').filter({ hasText: '分页对局 25' }).click();
  await expect.poll(() => page.evaluate(() => JSON.parse(localStorage.getItem('agentArenaSession')).gameId)).toBe(25);
  await expect(page.locator('#replays-page')).toHaveClass(/active/);
});
