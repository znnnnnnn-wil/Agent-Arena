const { test, expect } = require('@playwright/test');
const { installMockApi, PLAYERS } = require('./helpers/mock-api');

async function openBattle(page) {
  await installMockApi(page);
  await page.goto('/');
  await page.waitForFunction(() => window.AgentArena3D);
  await page.evaluate(players => {
    document.querySelectorAll('.page').forEach(element => element.classList.toggle('active', element.id === 'watch-page'));
    const battlePlayers = players.map((player, i) => ({ ...player, roleCode: null,
      region: ['A', 'B', 'C', 'D', 'E', 'A', 'B', 'D'][i], status: i === 5 ? 'DOWNED' : 'ALIVE',
      health: i === 5 ? 0 : 80, medkits: 0, food: 4, water: 4, armor: 10,
      armed: i === 2, weaponName: i === 2 ? '突击步枪' : null }));
    const game = { id: 88, mode: 'BATTLE_ROYALE', name: '林地生存验收局', status: 'RUNNING', currentRound: 4,
      currentActorPlayerId: 101, currentActorName: 'Alpha', currentActionType: 'GUARD', publicGame: true };
    const events = [{ sequenceNo: 101, eventType: 'BATTLE_ACTION_EXECUTED', actorPlayerId: 101,
      roundNo: 4, payload: { action: 'GUARD', targetPlayerId: 106, region: 'A' } }];
    renderRealWatch(game, battlePlayers, events, false);
    window.forestTestPayload = { game, players: battlePlayers, events };
    window.forestTestStage = window.AgentArena3D.mount(document.querySelector('[data-arena-3d="battle"]'), 'battle', window.forestTestPayload);
  }, PLAYERS);
  await expect(page.locator('[data-arena-3d="battle"]')).toHaveAttribute('data-arena3d-assets', '8');
}

test('林地对局动作服从真实状态，复活与再次倒地不会串场', async ({ page }) => {
  test.setTimeout(60_000);
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await openBattle(page);
  const arena = page.locator('[data-arena-3d="battle"]');
  await arena.scrollIntoViewIfNeeded();
  await arena.locator('canvas').evaluate(canvas => { canvas.dataset.reuseProbe = 'forest'; });
  await expect(arena).toHaveAttribute('data-arena3d-action', 'GUARD');
  const guard = await page.evaluate(() => {
    const stage = window.forestTestStage;
    return { targetRotation: stage.agents.get('106').userData.survivor.root.rotation.z,
      mode: stage.host.dataset.arena3dAssetMode, labels: stage.regionLabels.length };
  });
  expect(guard).toEqual({ targetRotation: -Math.PI / 2, mode: 'procedural-rigged', labels: 5 });

  await page.evaluate(() => {
    const payload = window.forestTestPayload;
    payload.players = payload.players.map(player => player.id === 106 ? { ...player, status: 'ALIVE', health: 30 } : player);
    payload.events.push({ sequenceNo: 102, eventType: 'BATTLE_PLAYER_REVIVED', actorPlayerId: 101,
      payload: { rescuerPlayerId: 101, playerId: 106, health: 30, region: 'A' } });
    window.forestTestStage.update(payload);
  });
  await expect(arena).toHaveAttribute('data-arena3d-action', 'REVIVE');
  expect(await page.evaluate(() => Math.abs(window.forestTestStage.agents.get('101').rotation.y))).toBeGreaterThan(1);
  await expect.poll(() => page.evaluate(() => window.forestTestStage.agents.get('101').userData.survivor.legs[0].bend.rotation.x)).toBeGreaterThan(.4);
  await expect.poll(() => page.evaluate(() => Math.abs(window.forestTestStage.agents.get('106').userData.survivor.root.rotation.z))).toBeLessThan(.01);
  await expect(arena.locator('[data-arena3d-player="106"]')).toHaveAttribute('data-player-status', 'ALIVE');

  await page.evaluate(() => {
    const payload = window.forestTestPayload;
    payload.players = payload.players.map(player => player.id === 106 ? { ...player, status: 'DOWNED', health: 0 } : player);
    payload.events.push({ sequenceNo: 103, eventType: 'BATTLE_ACTION_EXECUTED', actorPlayerId: 103,
      payload: { action: 'ATTACK', targetPlayerId: 106, damage: 40 } });
    window.forestTestStage.update(payload);
  });
  await expect(arena).toHaveAttribute('data-arena3d-action', 'ATTACK');
  await expect.poll(() => page.evaluate(() => window.forestTestStage.agents.get('103').userData.survivor.arms[1].bend.rotation.x)).toBeLessThan(-.3);
  expect(await page.evaluate(() => window.forestTestStage.agents.get('106').userData.survivor.root.rotation.z)).toBe(-Math.PI / 2);
  await expect(arena.locator('canvas')).toHaveAttribute('data-reuse-probe', 'forest');

  // Public views must hide a player as soon as their region is redacted.
  const hidden = await page.evaluate(() => {
    const payload = window.forestTestPayload;
    payload.players = payload.players.map(player => player.id === 103 ? { ...player, region: null } : player);
    window.forestTestStage.update(payload); window.forestTestStage.updateLabels();
    return window.forestTestStage.agents.get('103').visible;
  });
  expect(hidden).toBe(false);
  await expect(arena.locator('[data-arena3d-player="103"]')).toHaveCSS('pointer-events', 'none');
  await expect(arena.locator('[data-arena3d-player="103"]')).toBeHidden();

  const disposal = await page.evaluate(() => {
    const old = window.forestTestStage;
    let disposedTexture = false;
    old.forest.root.children.find(child => child.material?.map)?.material.map.addEventListener('dispose', () => { disposedTexture = true; });
    const next = window.AgentArena3D.mount(old.host, 'battle', { ...window.forestTestPayload,
      game: { ...window.forestTestPayload.game, id: 89 }, events: [] });
    return { oldDisposed: old.disposed, textureDisposed: disposedTexture, newReady: next.loadedAssetCount };
  });
  expect(disposal).toEqual({ oldDisposed: true, textureDisposed: true, newReady: 7 });
  expect(errors).toEqual([]);
});

test('减少动画时保留静态动作、状态和拖动镜头', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await openBattle(page);
  const result = await page.evaluate(() => {
    const stage = window.forestTestStage;
    const arm = stage.agents.get('101').userData.survivor.arms[1];
    return { loop: stage.animateContinuously, bend: arm.bend.rotation.x,
      downed: stage.agents.get('106').userData.survivor.root.rotation.z };
  });
  expect(result.loop).toBe(false);
  expect(result.bend).toBeLessThan(-.5);
  expect(result.downed).toBe(-Math.PI / 2);
  const arena = page.locator('[data-arena-3d="battle"]');
  await arena.scrollIntoViewIfNeeded();
  const before = await page.evaluate(() => ({ yaw: window.forestTestStage.cameraState.yaw,
    frames: window.forestTestStage.renderer.info.render.frame }));
  const bounds = await arena.boundingBox();
  await page.mouse.move(bounds.x + bounds.width * .5, bounds.y + bounds.height * .65);
  await page.mouse.down();
  await page.mouse.move(bounds.x + bounds.width * .65, bounds.y + bounds.height * .65, { steps: 5 });
  await page.mouse.up();
  const after = await page.evaluate(() => ({ yaw: window.forestTestStage.cameraState.yaw,
    frames: window.forestTestStage.renderer.info.render.frame }));
  expect(after.yaw).not.toBe(before.yaw);
  expect(after.frames).toBeGreaterThan(before.frames);
  expect(await page.evaluate(() => {
    const stage = window.forestTestStage;
    stage.update({ ...window.forestTestPayload, players: window.forestTestPayload.players.map(player => player.id === 106 ? { ...player, status: 'ELIMINATED' } : player) });
    return stage.agents.get('106').userData.renderedAction;
  })).toBe('ELIMINATED');
});
