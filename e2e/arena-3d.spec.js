const { test, expect } = require('@playwright/test');
const { installMockApi, PLAYERS, EVENTS } = require('./helpers/mock-api');
const { installTuringMockApi } = require('./helpers/turing-mock-api');

test('狼人杀模式持续复用并更新真实 WebGL 3D 舞台', async ({ page }) => {
  const runtimeErrors = [];
  page.on('pageerror', (error) => runtimeErrors.push(error.message));
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await installMockApi(page);
  await page.goto('/#clip=42:g42-s41-question');
  await expect(page.locator('[data-arena-3d="werewolf"] canvas')).toBeVisible();
  await expect(page.locator('[data-arena-3d="werewolf"] .arena3d-label')).toHaveCount(8);
  await expect(page.locator('[data-arena-3d="werewolf"]')).toHaveAttribute('data-arena3d-assets', '8', { timeout: 15000 });
  await expect(page.locator('[data-arena-3d="werewolf"] .arena3d-moment-card')).toHaveAttribute('data-action', 'talk');
  await expect(page.locator('[data-arena-3d="werewolf"]')).toHaveAttribute('data-arena3d-relation', 'SUSPECT');
  await expect(page.locator('[data-arena-3d="werewolf"]')).toHaveAttribute('data-arena3d-relation-count', '3');
  if (page.viewportSize().width <= 720) {
    const layout = await page.evaluate(() => {
      const arena = document.querySelector('.werewolf-arena-v2').getBoundingClientRect();
      const phase = document.querySelector('.wolf-phase-track').getBoundingClientRect();
      const relation = document.querySelector('.wolf-relation-switch').getBoundingClientRect();
      const center = document.querySelector('.wolf-center').getBoundingClientRect();
      const note = document.querySelector('.god-view-note').getBoundingClientRect();
      return {
        phaseBottom: phase.bottom,
        relationTop: relation.top,
        centerHeight: center.height,
        noteBottomGap: arena.bottom - note.bottom
      };
    });
    expect(layout.phaseBottom).toBeLessThanOrEqual(layout.relationTop);
    expect(layout.centerHeight).toBeLessThanOrEqual(120);
    expect(layout.noteBottomGap).toBeLessThanOrEqual(8);
  }
  await page.locator('[data-arena-3d="werewolf"] canvas').evaluate((canvas) => { canvas.dataset.reuseProbe = 'preserved'; });
  await page.locator('[data-wolf-relation]').first().click();
  await expect(page.locator('[data-arena-3d="werewolf"] canvas')).toHaveAttribute('data-reuse-probe', 'preserved');
  await expect(page.locator('[data-arena-3d="werewolf"]')).toHaveAttribute('data-arena3d-relation', 'VOTE');
  await expect(page.locator('[data-arena-3d="werewolf"]')).toHaveAttribute('data-arena3d-relation-count', '6');
  await page.evaluate(() => {
    const nextEvent = {
      sequenceNo: 46,
      eventType: 'PLAYER_VOTED',
      actorPlayerId: 101,
      roundNo: 2,
      phase: 'DAY_VOTE',
      payload: { targetPlayerId: 103, targetSeatNo: 3 }
    };
    renderRealWatch(
      { ...lastRealGame, currentActorPlayerId: 101, currentActorName: 'Alpha', currentActionType: 'VOTE' },
      lastRealPlayers,
      [...currentRealEvents, nextEvent],
      false
    );
  });
  await expect(page.locator('[data-arena-3d="werewolf"] .arena3d-moment-card')).toHaveAttribute('data-action', 'vote');
  await expect(page.locator('[data-arena-3d="werewolf"]')).toHaveAttribute('data-arena3d-last-event', '46');
  await expect(page.locator('[data-arena-3d="werewolf"] canvas')).toHaveAttribute('data-reuse-probe', 'preserved');
  await page.evaluate(() => {
    renderRealWatch(
      { ...lastRealGame, currentPhase: 'NIGHT_GUARD', currentRound: 3, currentActorPlayerId: 105, currentActorName: 'Echo' },
      lastRealPlayers,
      currentRealEvents,
      false
    );
  });
  await expect(page.locator('[data-arena-3d="werewolf"]')).toHaveAttribute('data-arena3d-phase-theme', 'night');
  await expect(page.locator('.werewolf-arena-v2 .wolf-center blockquote')).not.toContainText('为什么这一轮突然准备改投5号');
  await page.evaluate(() => {
    renderRealWatch(
      { ...lastRealGame, id: 99, name: '狼人杀重赛局', currentPhase: 'NIGHT_WOLF', currentRound: 1 },
      lastRealPlayers,
      [],
      false
    );
  });
  await expect(page.locator('[data-arena-3d="werewolf"] canvas')).not.toHaveAttribute('data-reuse-probe', 'preserved');
  await expect(page.locator('[data-arena-3d="werewolf"]')).toHaveAttribute('data-arena3d-assets', '8', { timeout: 15000 });
  expect(runtimeErrors).toEqual([]);
});

test('图灵模式挂载真实 WebGL 3D 舞台', async ({ page }) => {
  const runtimeErrors = [];
  page.on('pageerror', (error) => runtimeErrors.push(error.message));
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await installTuringMockApi(page, { stage: 'qna' });
  await page.addInitScript(() => {
    localStorage.setItem('agentArenaSession', JSON.stringify({ gameId: 77, mode: 'TURING_HUNT' }));
  });
  await page.goto('/');
  await page.evaluate(() => goTo('watch'));
  await expect(page.locator('[data-arena-3d="turing"] canvas')).toBeVisible({ timeout: 15000 });
  await expect(page.locator('[data-arena-3d="turing"] .arena3d-label')).toHaveCount(6);
  await expect(page.locator('[data-arena-3d="turing"]')).toHaveAttribute('data-arena3d-assets', '6', { timeout: 15000 });
  expect(runtimeErrors).toEqual([]);
});

test('大逃杀第三阶段挂载协作信标并识别社会事件', async ({ page }) => {
  const runtimeErrors = [];
  page.on('pageerror', (error) => runtimeErrors.push(error.message));
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await installMockApi(page);
  await page.goto('/');
  const players = PLAYERS.map((player, index) => ({
    ...player, roleCode: null, region: ['A', 'B', 'C', 'D', 'E', 'A', 'B', 'D'][index],
    status: index === 5 ? 'DOWNED' : 'ALIVE', health: index === 5 ? 0 : 80,
    food: 4, water: 4, armor: 10, medkits: 1
  }));
  const events = [{
    sequenceNo: 91, eventType: 'BATTLE_OBJECTIVE_CONTRIBUTED', actorPlayerId: 103,
    roundNo: 4, phase: 'ACTION', payload: { objectiveId: 'beacon-88', region: 'C' }
  }];
  await page.evaluate(({ battlePlayers, battleEvents }) => {
    document.querySelectorAll('.page').forEach((element) => element.classList.toggle('active', element.id === 'watch-page'));
    renderRealWatch({ id: 88, name: '第三阶段测试局', mode: 'BATTLE_ROYALE', status: 'RUNNING', currentRound: 4,
      currentActorPlayerId: 103, currentActorName: 'Charlie', currentActionType: 'SEARCH', publicGame: true },
    battlePlayers, battleEvents, false);
  }, { battlePlayers: players, battleEvents: events });
  const arena = page.locator('[data-arena-3d="battle"]');
  await expect(arena.locator('canvas')).toBeVisible();
  await expect(arena).toHaveAttribute('data-arena3d-stage3', 'social-simulation');
  await expect(arena.locator('.arena3d-moment-card')).toHaveAttribute('data-action', 'contribute');
  await expect(arena).toHaveAttribute('data-arena3d-assets', '8', { timeout: 15000 });
  expect(runtimeErrors).toEqual([]);
});

test('@visual 大逃杀挂载 3D 岛屿、战斗轨迹与玩家标签', async ({ page }, testInfo) => {
  const runtimeErrors = [];
  page.on('pageerror', (error) => runtimeErrors.push(error.message));
  await installMockApi(page);
  await page.goto('/');
  const players = PLAYERS.map((player, index) => ({
    ...player,
    roleCode: null,
    region: ['A', 'B', 'C', 'D', 'E', 'A', 'B', 'D'][index],
    health: [88, 64, 100, 31, 76, 0, 53, 92][index],
    armor: [20, 0, 35, 4, 18, 0, 11, 28][index],
    weaponName: index % 3 ? '突击步枪' : null,
    weaponLevel: index % 3 ? 2 : 0,
    ammo: index % 3 ? 18 : 0,
    armed: index % 3 !== 0,
    kills: index === 2 ? 2 : 0
  }));
  const events = [
    {
      sequenceNo: 51,
      eventType: 'BATTLE_SUPPLY_DROP',
      actorPlayerId: null,
      roundNo: 5,
      phase: 'ACTION',
      payload: { region: 'C' }
    },
    {
      sequenceNo: 52,
      eventType: 'BATTLE_ACTION_EXECUTED',
      actorPlayerId: 103,
      roundNo: 5,
      phase: 'ACTION',
      payload: { action: 'ATTACK', targetPlayerId: 108, damage: 38, health: 100, region: 'C' }
    }
  ];
  await page.evaluate(({ battlePlayers, battleEvents }) => {
    document.querySelectorAll('.page').forEach((element) => {
      element.classList.toggle('active', element.id === 'watch-page');
    });
    const game = {
      id: 88,
      name: '3D 生存岛验收局',
      mode: 'BATTLE_ROYALE',
      status: 'RUNNING',
      currentRound: 5,
      currentActorPlayerId: 103,
      currentActorName: 'Charlie',
      currentActionType: 'ATTACK',
      publicGame: true
    };
    renderRealWatch(game, battlePlayers, battleEvents, false);
  }, { battlePlayers: players, battleEvents: events });
  await expect(page.locator('.battle-map.arena3d-active canvas')).toBeVisible();
  await expect(page.locator('.battle-map .arena3d-label')).toHaveCount(8);
  await expect(page.locator('[data-arena-3d="battle"]')).toHaveAttribute('data-arena3d-assets', '8', { timeout: 15000 });
  await expect(page.locator('.battle-map .battle-live-feed')).toContainText('攻击');
  expect(runtimeErrors).toEqual([]);
  await testInfo.attach('battle-royale-3d-stage', {
    body: await page.locator('.battle-map').screenshot(),
    contentType: 'image/png'
  });
  await expect(page.locator('.battle-map')).toHaveScreenshot('battle-3d-stage.png');
});
