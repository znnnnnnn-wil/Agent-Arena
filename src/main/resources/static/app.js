const pages = document.querySelectorAll('.page');
const navItems = document.querySelectorAll('.nav-item');
const toast = document.getElementById('toast');

function showToast(message) {
  if (!toast) return;
  toast.textContent = message;
  toast.classList.add('show');
  clearTimeout(window.toastTimer);
  window.toastTimer = setTimeout(() => toast.classList.remove('show'), 2200);
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[character]);
}

function goTo(page) {
  if (['home', 'create', 'models'].includes(page) && /^#public-game=\d+$/.test(window.location.hash)) {
    history.replaceState(null, '', window.location.pathname + window.location.search);
  } else if (/^#(?:create|models|replays|login|register)$/.test(window.location.hash)
      && window.location.hash !== `#${page}`) {
    history.replaceState(null, '', window.location.pathname + window.location.search);
  }
  if (page !== 'watch') {
    stopAutoPlay();
    if (liveEventSource) { liveEventSource.close(); liveEventSource = null; liveStreamGameId = null; }
  }
  pages.forEach((item) => item.classList.toggle('active', item.id === `${page}-page`));
  navItems.forEach((item) => item.classList.toggle('active', item.dataset.page === page));
  window.scrollTo({ top: 0, behavior: 'smooth' });
  if (page === 'create' && createStep === 3) setTimeout(loadSeatModelOptions, 0);
  if (page === 'watch') setTimeout(loadCurrentGame, 0);
  if (page === 'replays') setTimeout(loadReplayPage, 0);
  if (page === 'home') setTimeout(loadLobbyGames, 0);
}

document.querySelectorAll('[data-page]').forEach((button) => {
  button.addEventListener('click', () => goTo(button.dataset.page));
});

const MODE_CONTENT = {
  WEREWOLF: {
    title: '一场可追踪的阵营推理实验', difficulty: '策略深度 · 高',
    previewTitle: '8 人进阶狼人杀', preview: '2 狼人 · 预言家 · 女巫 · 守卫 · 3 平民',
    seats: ['W', 'W', 'P', '女', 'G', 'V', 'V', 'V'],
    abilities: [['语言推理', 92], ['身份伪装', 88], ['长期记忆', 76]],
    phases: [['01', '狼人密谋', '秘密选择袭击目标'], ['02', '神职行动', '查验、守护与药剂决策'], ['03', '公开发言', '逐席陈述与交叉质疑'], ['04', '放逐投票', '多数决与阵营结算']],
    outcomes: [['好人阵营', '找出并放逐全部狼人'], ['狼人阵营', '让狼人数量追平其他存活者'], ['观战重点', '发言矛盾、投票流向、夜间行动反馈']]
  },
  BATTLE_ROYALE: {
    title: '一场受资源与空间挤压的生存决策实验', difficulty: '策略深度 · 中高',
    previewTitle: '8 人标准节点大逃杀', preview: '5 个特色区域 · 动态缩圈 · 装备搜寻 · 空投争夺',
    seats: ['1', '2', '3', '4', '5', '6', '7', '8'],
    abilities: [['风险评估', 90], ['资源规划', 84], ['时机选择', 87]],
    phases: [['01', '区域选择', '移动到资源区或安全点'], ['02', '搜寻整备', '补充武器、护甲与治疗'], ['03', '遭遇行动', '进攻、隐藏或转移'], ['04', '缩圈结算', '承受区域压力并进入下一轮']],
    outcomes: [['唯一胜者', '最后一名存活的智能体获胜'], ['环境压力', '安全区缩小与空投改变区域价值'], ['观战重点', '移动轨迹、生命值、装备差和交战选择']]
  }
};

const modeCards = document.querySelectorAll('.mode-card');
let selectedMode = 'WEREWOLF';

function renderModeDetails() {
  const content = MODE_CONTENT[selectedMode];
  const title = document.getElementById('selected-mode-title');
  const preview = document.querySelector('.setup-preview > div:first-child p');
  const previewSeats = document.querySelector('.preview-seats');
  const detailTitle = document.getElementById('mode-detail-title');
  const difficulty = document.getElementById('mode-difficulty');
  const abilityList = document.getElementById('mode-ability-list');
  const phaseList = document.getElementById('mode-phase-list');
  const outcomeList = document.getElementById('mode-outcome-list');
  if (title) title.textContent = content.previewTitle;
  if (preview) preview.textContent = content.preview;
  if (previewSeats) previewSeats.innerHTML = content.seats.map((seat) => `<i>${escapeHtml(seat)}</i>`).join('');
  if (detailTitle) detailTitle.textContent = content.title;
  if (difficulty) difficulty.textContent = content.difficulty;
  if (abilityList) abilityList.innerHTML = content.abilities.map(([label, value]) => `<div><span>${escapeHtml(label)}<b>${value}%</b></span><i><em style="width:${value}%"></em></i></div>`).join('');
  if (phaseList) phaseList.innerHTML = content.phases.map(([number, label, copy]) => `<li><b>${number}</b><span><strong>${escapeHtml(label)}</strong><small>${escapeHtml(copy)}</small></span></li>`).join('');
  if (outcomeList) outcomeList.innerHTML = content.outcomes.map(([label, copy]) => `<div><b>${escapeHtml(label)}</b><span>${escapeHtml(copy)}</span></div>`).join('');
}

modeCards.forEach((card) => card.addEventListener('click', () => {
  modeCards.forEach((item) => item.classList.remove('selected'));
  card.classList.add('selected');
  selectedMode = card.dataset.mode === 'BATTLE_ROYALE' ? 'BATTLE_ROYALE' : 'WEREWOLF';
  configuredGameName = selectedMode === 'WEREWOLF' ? 'AI 狼人杀' : 'AI 大逃杀';
  renderModeDetails();
}));

let createStep = 1;
let modelDraft = null;
let seatModelConfigIds = Array(8).fill(null);
let configuredPlayerCount = 8;
let configuredMaxRounds = 20;
let configuredGameName = 'AI 狼人杀';
let configuredPublicGame = true;
const nextButton = document.getElementById('next-step');
const createMain = document.querySelector('.create-main');
const modeGrid = document.querySelector('.mode-options');
const setupPreview = document.querySelector('.setup-preview');
const stepPanel = document.createElement('div');
stepPanel.className = 'dynamic-step-panel';
const createFooter = document.querySelector('.create-footer');
if (createMain && createFooter) createMain.insertBefore(stepPanel, createFooter);
const stepTitle = document.querySelector('.create-head > div > span');
const createHeading = document.querySelector('.create-head h1');
const createDescription = document.querySelector('.create-head p');
const progressRing = document.querySelector('.progress-ring');

function renderCreateStep() {
  if (!stepPanel) return;
  const rulePanel = selectedMode === 'WEREWOLF'
    ? `<div class="rules-workbench wolf-rules"><header><div><span class="kicker">ADVANCED CLASSIC</span><h3>8 人进阶阵营局</h3><p>固定角色配比保证信息量与反制能力平衡，身份在服务端随机分配。</p></div><span class="rule-lock">固定 8 席</span></header><div class="role-composition"><article><b>2</b><span>狼人<small>夜间协作袭击</small></span></article><article><b>1</b><span>预言家<small>每夜查验阵营</small></span></article><article><b>1</b><span>女巫<small>解药与毒药各一次</small></span></article><article><b>1</b><span>守卫<small>守护且不能连续同守</small></span></article><article><b>3</b><span>平民<small>依靠发言与投票</small></span></article></div><div class="rule-phase-strip"><span>夜间密谋</span><i>→</i><span>神职行动</span><i>→</i><span>白天发言</span><i>→</i><span>放逐投票</span></div><div class="rule-chips"><span>女巫双药各一次</span><span>守卫不可连续同守</span><span>同守同救仍可存活</span><span>平票无人出局</span><span>狼人追平人数获胜</span></div></div>`
    : `<div class="rules-workbench battle-rules"><header><div><span class="kicker">SURVIVAL PRESETS</span><h3>选择生存节奏</h3><p>预设会真实调整参与人数与最大回合，仍可在下方精细修改。</p></div><span class="rule-lock">6–10 席</span></header><div class="battle-presets"><button type="button" data-players="6" data-rounds="12"><b>闪电局</b><span>6 人 · 12 回合</span><small>交战更早，适合快速观察</small></button><button type="button" class="active" data-players="8" data-rounds="20"><b>标准局</b><span>8 人 · 20 回合</span><small>资源、移动和战斗更均衡</small></button><button type="button" data-players="10" data-rounds="30"><b>长线局</b><span>10 人 · 30 回合</span><small>联盟与区域博弈更复杂</small></button></div><div class="battle-tuning"><label>玩家人数 <span><input id="player-count" type="range" value="${configuredPlayerCount}" min="6" max="10"><output id="player-count-output">${configuredPlayerCount}</output></span></label><label>最大回合 <span><input id="max-rounds" type="range" value="${configuredMaxRounds}" min="12" max="30"><output id="max-rounds-output">${configuredMaxRounds}</output></span></label></div><div class="region-strip"><span>中央废墟<small>武器 / 弹药</small></span><span>北部密林<small>食物 / 医疗</small></span><span>东岸码头<small>饮水 / 弹药</small></span><span>南部工事<small>护甲 / 弹药</small></span><span>西侧营地<small>医疗 / 补给</small></span></div></div>`;
  const confirmPanel = `<div class="confirm-workbench"><div class="confirm-summary"><span class="kicker">MATCH SUMMARY</span><h3>${escapeHtml(MODE_CONTENT[selectedMode].previewTitle)}</h3><p>${selectedMode === 'WEREWOLF' ? '固定 8 席 · 进阶角色 · 阵营胜负' : `${configuredPlayerCount} 名生存者 · 最多 ${configuredMaxRounds} 回合 · 唯一胜者`}</p><div><span><b>${seatModelConfigIds.filter(Boolean).length}</b> 个模型席位</span><span><b>${seatModelConfigIds.filter((id) => !id).length}</b> 个本地策略</span><span><b>完整</b> 事件复盘</span></div></div><div class="confirm-fields"><label>对局名称<input id="game-name" maxlength="128" value="${escapeHtml(configuredGameName)}" required></label><label class="visibility-option"><input id="public-game" type="checkbox" ${configuredPublicGame ? 'checked' : ''}><span><b>公开观战</b><small>其他人只会看到脱敏后的公开事件；隐藏身份与夜间秘密不会泄露。</small></span></label></div></div>`;
  const panels = {
    2: rulePanel,
    3: '<div class="model-config-panel seat-assignment"><div class="seat-assignment-head"><div><span class="kicker">AGENT SEATS</span><h3>为席位分配大模型</h3><p>角色会在服务端随机分配；每个席位可选择任意已保存模型，也可以使用本地策略。</p></div><button class="secondary" type="button" id="open-model-center">＋ 新建模型配置</button></div><div id="seat-config-list" class="seat-config-list"><p>正在加载模型配置…</p></div><small class="model-hint">没有选择模型的席位将使用本地确定性策略。</small></div>',
    4: confirmPanel
  };
  stepPanel.innerHTML = panels[createStep] || '';
  if (createStep === 3) loadSeatModelOptions();
  const headings = ['选择一个游戏世界', '配置游戏规则', '选择智能体席位', '确认并开始'];
  const descriptions = ['选择一个规则世界。', '设置玩家人数、回合和胜利条件。', '为各个席位配置模型或本地策略。', '检查配置，然后创建正式对局。'];
  if (stepTitle) stepTitle.textContent = `步骤 ${createStep} / 4`;
  if (createHeading) createHeading.textContent = headings[createStep - 1];
  if (createDescription) createDescription.textContent = descriptions[createStep - 1];
  if (progressRing) progressRing.firstChild.textContent = `${createStep * 25}`;
  if (modeGrid) modeGrid.style.display = createStep === 1 ? '' : 'none';
  const modeIntelligence = document.getElementById('mode-intelligence');
  if (modeIntelligence) modeIntelligence.style.display = createStep === 1 ? '' : 'none';
  if (setupPreview) setupPreview.style.display = createStep === 1 ? '' : 'none';
  wireCreateStepControls();
}

function wireCreateStepControls() {
  if (createStep !== 2 || selectedMode !== 'BATTLE_ROYALE') return;
  const playerInput = document.getElementById('player-count');
  const roundInput = document.getElementById('max-rounds');
  const updateOutputs = () => {
    const players = Number(playerInput?.value || 8);
    const rounds = Number(roundInput?.value || 20);
    const playerOutput = document.getElementById('player-count-output');
    const roundOutput = document.getElementById('max-rounds-output');
    if (playerOutput) playerOutput.textContent = String(players);
    if (roundOutput) roundOutput.textContent = String(rounds);
    document.querySelectorAll('.battle-presets button').forEach((button) => button.classList.toggle('active', Number(button.dataset.players) === players && Number(button.dataset.rounds) === rounds));
  };
  playerInput?.addEventListener('input', updateOutputs);
  roundInput?.addEventListener('input', updateOutputs);
  document.querySelectorAll('.battle-presets button').forEach((button) => button.addEventListener('click', () => {
    if (playerInput) playerInput.value = button.dataset.players;
    if (roundInput) roundInput.value = button.dataset.rounds;
    updateOutputs();
  }));
  updateOutputs();
}

function captureRuleConfig() {
  if (selectedMode !== 'BATTLE_ROYALE') return;
  configuredPlayerCount = Math.max(6, Math.min(10, Number(document.getElementById('player-count')?.value || 8)));
  configuredMaxRounds = Math.max(12, Math.min(30, Number(document.getElementById('max-rounds')?.value || 20)));
  if (seatModelConfigIds.length !== configuredPlayerCount) {
    seatModelConfigIds = Array.from({ length: configuredPlayerCount }, (_, index) => seatModelConfigIds[index] || null);
  }
}

function captureConfirmation() {
  const nameInput = document.getElementById('game-name');
  if (nameInput) configuredGameName = nameInput.value.trim();
  configuredPublicGame = document.getElementById('public-game')?.checked ?? configuredPublicGame;
}

function captureModelDraft() {
  const selects = document.querySelectorAll('.seat-model-select');
  if (!selects.length) return;
  seatModelConfigIds = Array.from(selects).map((select) => select.value ? Number(select.value) : null);
}

async function loadSeatModelOptions() {
  const container = document.getElementById('seat-config-list');
  if (!container) return;
  try {
    const visitor = await ensureVisitorSession();
    const configs = await apiRequest('/api/model-configs');
    const roleNames = selectedMode === 'WEREWOLF'
      ? Array.from({ length: 8 }, (_, index) => `匿名席位 ${index + 1}`)
      : Array.from({ length: configuredPlayerCount }, (_, index) => `生存者 ${index + 1}`);
    const options = configs.map((config) => `<option value="${Number(config.id)}">${escapeHtml(config.name)} · ${escapeHtml(config.provider)}/${escapeHtml(config.modelName)}</option>`).join('');
    container.innerHTML = roleNames.map((role, index) => `<label><span><b>${index + 1}</b>${role}</span><select class="seat-model-select"><option value="">本地策略（不调用模型）</option>${options}</select></label>`).join('');
    container.querySelectorAll('.seat-model-select').forEach((select, index) => {
      if (seatModelConfigIds[index]) select.value = String(seatModelConfigIds[index]);
    });
    document.getElementById('open-model-center')?.addEventListener('click', () => {
      captureModelDraft();
      goTo('models');
      loadSavedModels();
    });
  } catch (error) {
    container.innerHTML = `<p>模型配置加载失败：${escapeHtml(error.message)}</p>`;
  }
}
async function apiRequest(url, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 35000);
  const { headers = {}, signal, ...fetchOptions } = options;
  let visitorToken = null;
  try { visitorToken = JSON.parse(localStorage.getItem('agentArenaUser') || 'null')?.visitorToken || null; } catch { /* no token */ }
  try {
    const response = await fetch(url, {
      ...fetchOptions,
      headers: { 'Content-Type': 'application/json', ...(visitorToken ? { 'X-Visitor-Token': visitorToken } : {}), ...headers },
      signal: signal || controller.signal
    });
    const text = await response.text();
    let body;
    try { body = text ? JSON.parse(text) : {}; }
    catch { throw new Error(`服务返回了无法解析的响应（HTTP ${response.status}）`); }
    if (!response.ok || body.code !== '00000') {
      const requestError = new Error(body.msg || `请求失败（HTTP ${response.status}）`);
      requestError.status = response.status;
      requestError.code = body.code;
      throw requestError;
    }
    return body.data;
  } catch (error) {
    if (error.name === 'AbortError') throw new Error('请求超过 35 秒，请检查后端或模型服务');
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

async function createRealGame() {
  nextButton.disabled = true;
  nextButton.textContent = '正在创建…';
  try {
    const visitor = await ensureVisitorSession();
    const selectedIds = seatModelConfigIds.filter((id) => id != null);
    const modelConfigIds = selectedIds.length ? seatModelConfigIds : [];
    captureConfirmation();
    if (!configuredGameName) throw new Error('请填写对局名称');
    const common = { name: configuredGameName, publicGame: configuredPublicGame, modelConfigIds };
    const game = selectedMode === 'WEREWOLF'
      ? await apiRequest('/api/games/werewolf', { method: 'POST', body: JSON.stringify(common) })
      : await apiRequest('/api/games/battle-royale', { method: 'POST', body: JSON.stringify({ ...common, playerCount: configuredPlayerCount, maxRounds: configuredMaxRounds }) });
    const startPath = selectedMode === 'WEREWOLF' ? 'start' : 'battle-start';
    await apiRequest(`/api/games/${game.id}/${startPath}`, { method: 'POST' });
    localStorage.setItem('agentArenaSession', JSON.stringify({ gameId: game.id, mode: selectedMode }));
    showToast(`真实对局 #${game.id} 已写入 MySQL`);
    setTimeout(() => goTo('watch'), 450);
  } catch (error) {
    showToast(`创建失败：${error.message}`);
  } finally {
    nextButton.disabled = false;
    nextButton.textContent = '创建对局';
  }
}

if (nextButton) nextButton.addEventListener('click', async () => {
  if (createStep === 4) {
    await createRealGame();
    return;
  }
  if (createStep === 2) captureRuleConfig();
  if (createStep === 3) captureModelDraft();
  if (createStep === 4) captureConfirmation();
  createStep = Math.min(4, createStep + 1);
  document.querySelectorAll('.steps li').forEach((step, index) => {
    step.classList.toggle('active', index + 1 === createStep);
    step.classList.toggle('done', index + 1 < createStep);
  });
  const labels = ['下一步：配置规则 →', '下一步：选择智能体 →', '下一步：确认并开始 →', '创建对局'];
  nextButton.innerHTML = labels[createStep - 1];
  renderCreateStep();
  showToast(createStep === 4 ? '配置完成，可以创建对局了' : `已进入第 ${createStep} 步`);
});

document.querySelectorAll('.steps li').forEach((item, index) => {
  item.style.cursor = 'pointer';
  item.addEventListener('click', () => {
    if (createStep === 2) captureRuleConfig();
    if (createStep === 3) captureModelDraft();
    if (createStep === 4) captureConfirmation();
    createStep = index + 1;
    document.querySelectorAll('.steps li').forEach((step, stepIndex) => {
      step.classList.toggle('active', stepIndex + 1 === createStep);
      step.classList.toggle('done', stepIndex + 1 < createStep);
    });
    const labels = ['下一步：配置规则 →', '下一步：选择智能体 →', '下一步：确认并开始 →', '创建对局'];
    nextButton.innerHTML = labels[createStep - 1];
    renderCreateStep();
  });
});

renderModeDetails();

async function shareCurrentGame() {
  try {
    const session = getGameSession();
    if (!session?.gameId) throw new Error('没有可分享的对局');
    await navigator.clipboard.writeText(`${window.location.origin}${window.location.pathname}#public-game=${session.gameId}`);
    showToast('对局链接已复制');
  } catch {
    showToast('当前浏览器未授权复制，请复制地址栏链接');
  }
}

let currentRealEvents = [];
let liveEventSource = null;
let liveStreamGameId = null;

function getGameSession() {
  const publicMatch = window.location.hash.match(/^#public-game=(\d+)$/);
  if (publicMatch) return { gameId: Number(publicMatch[1]), publicView: true };
  try { return JSON.parse(localStorage.getItem('agentArenaSession') || 'null'); } catch { return null; }
}

function gameApiBase(session) {
  return session.publicView ? '/api/public/games' : '/api/games';
}

function connectLiveStream(game, session) {
  if (!game.publicGame || liveStreamGameId === game.id) return;
  if (liveEventSource) liveEventSource.close();
  liveStreamGameId = game.id;
  liveEventSource = new EventSource(`/api/public/games/${game.id}/stream`);
  liveEventSource.addEventListener('game-event', () => loadCurrentGame(true));
  liveEventSource.onerror = () => { if (session.publicView) showToast('实时流暂时断开，浏览器会自动重连'); };
}

async function loadCurrentGame(incremental = false) {
  const session = getGameSession();
  if (!session) {
    showToast('请先创建一场对局');
    return;
  }
  try {
    const base = gameApiBase(session);
    const afterSequence = incremental && currentRealEvents.length
      ? Math.max(...currentRealEvents.map((event) => Number(event.sequenceNo) || 0)) : 0;
    const [game, players, events] = await Promise.all([
      apiRequest(`${base}/${session.gameId}`),
      apiRequest(`${base}/${session.gameId}/players`),
      apiRequest(`${base}/${session.gameId}/replay?afterSequence=${afterSequence}&limit=500`)
    ]);
    currentRealEvents = incremental
      ? [...new Map([...currentRealEvents, ...events].map((event) => [Number(event.sequenceNo), event])).values()]
          .sort((left, right) => Number(left.sequenceNo) - Number(right.sequenceNo))
      : events;
    window.currentRealPlayers = players;
    renderRealWatch(game, players, currentRealEvents, session.publicView === true);
    connectLiveStream(game, session);
    return game;
  } catch (error) {
    showToast(`加载真实对局失败：${error.message}`);
  }
}

async function loadReplayPage() {
  const container = document.getElementById('replay-content');
  const session = getGameSession();
  if (!container) return;
  if (!session) {
    container.innerHTML = '<div class="empty-state compact"><span>◫</span><h2>还没有可复盘的对局</h2><p>创建并运行一场对局后，这里会自动生成复盘。</p><button class="primary" data-replay-create>创建对局</button></div>';
    container.querySelector('[data-replay-create]')?.addEventListener('click', () => goTo('create'));
    return;
  }
  container.innerHTML = '<div class="real-empty">正在整理事件与结算数据…</div>';
  try {
    const base = gameApiBase(session);
    const [game, players, events, summary] = await Promise.all([
      apiRequest(`${base}/${session.gameId}`),
      apiRequest(`${base}/${session.gameId}/players`),
      apiRequest(`${base}/${session.gameId}/replay?limit=500`),
      apiRequest(`${base}/${session.gameId}/summary`)
    ]);
    const groups = groupGameEvents(events).slice(-18).reverse();
    const winnerText = summary.winner && summary.winner !== 'null' ? summary.winner : game.status === 'FINISHED' ? '已结算' : '对局进行中';
    const playerRows = [...players].sort((a, b) => game.mode === 'BATTLE_ROYALE'
      ? (b.kills || 0) - (a.kills || 0) || (b.totalDamage || 0) - (a.totalDamage || 0)
      : (a.status === 'ALIVE' ? -1 : 1) - (b.status === 'ALIVE' ? -1 : 1));
    container.innerHTML = `<div class="replay-title"><div><span>${escapeHtml(gameModeLabel(game.mode))}</span><h2>${escapeHtml(game.name)} · #${game.id}</h2><p>第 ${game.currentRound || 1} 轮 · ${escapeHtml(gameStatusLabel(game.status))}</p></div><b>${escapeHtml(winnerText)}</b></div>
      <div class="replay-stats"><article><span>事件总数</span><b>${summary.eventCount || 0}</b></article><article><span>行动数量</span><b>${groupGameEvents(events).length}</b></article><article><span>淘汰人数</span><b>${summary.eliminationCount || 0}</b></article><article><span>当前轮次</span><b>${game.currentRound || 1}</b></article></div>
      <div class="replay-grid"><section><header><h3>关键转折</h3><span>${summary.keyEvents?.length || 0} 条</span></header><div class="key-event-list">${summary.keyEvents?.length ? summary.keyEvents.map((item, index) => `<article><i>${String(index + 1).padStart(2, '0')}</i><p>${escapeHtml(item)}</p></article>`).join('') : '<p class="muted-copy">对局继续推进后会生成关键事件。</p>'}</div></section>
      <aside><header><h3>${game.mode === 'BATTLE_ROYALE' ? '生存排名' : '角色结局'}</h3><span>${players.length} 名玩家</span></header><div class="replay-player-list">${playerRows.map((player, index) => `<article><i>${index + 1}</i><div><b>${escapeHtml(player.displayName)}</b><span>${game.mode === 'BATTLE_ROYALE' ? `${escapeHtml(player.weaponName || '徒手')} · 伤害 ${player.totalDamage || 0}` : escapeHtml(werewolfRoleLabel(player.roleCode))}</span></div><em>${game.mode === 'BATTLE_ROYALE' ? `☠ ${player.kills || 0}` : player.status === 'ALIVE' ? '存活' : '出局'}</em></article>`).join('')}</div></aside></div>
      <section class="replay-timeline"><header><h3>最近事件</h3><span>按时间倒序</span></header>${groups.map((group) => { const event = group.primary || group.decision; const info = describeGameEvent(group, players); return `<article><i>${escapeHtml(info[0])}</i><div><b>${escapeHtml(info[1])}</b><p>${escapeHtml(info[2])}</p></div><span>第 ${event.roundNo} 轮 · #${event.sequenceNo}</span></article>`; }).join('') || '<p class="muted-copy">暂无事件。</p>'}</section>`;
  } catch (error) {
    container.innerHTML = `<div class="empty-state compact"><span>!</span><h2>复盘加载失败</h2><p>${escapeHtml(error.message)}</p></div>`;
  }
}

function groupGameEvents(events) {
  const groups = [];
  events.forEach((event) => {
    if (event.eventType === 'AI_DECISION') {
      groups.push({ primary: null, decision: event, endSequence: event.sequenceNo, phaseChangedTo: null });
    } else if (event.eventType === 'PHASE_CHANGED' && groups.length) {
      groups[groups.length - 1].phaseChangedTo = event.payload?.phase || event.phase;
      groups[groups.length - 1].endSequence = event.sequenceNo;
    } else if (groups.length && groups[groups.length - 1].decision && !groups[groups.length - 1].primary) {
      groups[groups.length - 1].primary = event;
      groups[groups.length - 1].endSequence = event.sequenceNo;
    } else {
      groups.push({ primary: event, decision: null, endSequence: event.sequenceNo, phaseChangedTo: null });
    }
  });
  return groups;
}

function describeGameEvent(group, players) {
  const event = group.primary || group.decision;
  const actor = players.find((player) => player.id === event.actorPlayerId);
  const targetId = event.payload?.targetPlayerId || event.payload?.playerId;
  const target = players.find((player) => player.id === targetId);
  const actorName = actor?.displayName || (event.actorPlayerId ? `玩家 #${event.actorPlayerId}` : '系统');
  const targetName = target?.displayName || (targetId ? `玩家 #${targetId}` : '');
  const descriptions = {
    WOLF_TARGET_SELECTED: ['🌙', '狼人完成夜间选择', `${actorName} 已提交袭击目标，目标信息保持隐藏。`],
    GUARD_PROTECTED: ['🛡', '守卫完成守护', `${actorName} 已选择本夜守护目标。`],
    SEER_CHECKED: ['🔮', '预言家完成查验', `${actorName} 已获得一名玩家的阵营信息。`],
    WITCH_ACTION_COMPLETED: ['🧪', '女巫完成行动', `${actorName} 选择了 ${event.payload?.action || '行动'}。`],
    PLAYER_ELIMINATED: ['☠', '玩家出局', `${targetName || actorName} 因 ${event.payload?.reason || '游戏规则'} 被淘汰。`],
    PLAYER_SPOKE: ['💬', '玩家发言', `${actorName}：${event.payload?.message || '已完成公开发言。'}`],
    PLAYER_VOTED: ['🗳', '玩家投票', `${actorName} 投票给 ${targetName || '目标玩家'}。`],
    VOTE_TIED: ['⚖', '投票平局', '本轮投票没有产生唯一放逐目标。'],
    VOTE_RESULT: ['◉', '投票结果', `最高票候选：${(event.payload?.candidateIds || []).map((id) => players.find((player) => player.id === id)?.displayName || `#${id}`).join('、') || '无'}。`],
    DAWN_RESULT: ['☀', event.payload?.peaceful ? '平安夜' : '天亮结算', event.payload?.peaceful ? '昨夜无人出局。' : `昨夜共有 ${(event.payload?.eliminatedIds || []).length} 名玩家出局。`],
    GAME_FINISHED: ['🏆', '对局结束', `获胜阵营：${event.payload?.winner || '待确认'}`],
    BATTLE_ACTION_EXECUTED: ['⚔', '生存行动', `${actorName} 完成了 ${battleActionLabel(event.payload?.action)}。`],
    BATTLE_ZONE_UPDATED: ['◎', '安全区收缩', `毒圈进入第 ${event.payload?.ringStage || '-'} 阶段，安全区域：${(event.payload?.safeRegions || []).join('、')}。`],
    BATTLE_ZONE_DAMAGE: ['☣', '受到毒圈伤害', `${actorName} 在 ${event.payload?.region || '圈外'} 受到 ${event.payload?.damage || 0} 点伤害，剩余生命 ${event.payload?.health ?? '-'}。`],
    BATTLE_SUPPLY_DROP: ['▣', '空投已抵达', `高级物资空投落在 ${event.payload?.region || '-'} 区，搜索该区域可获得额外奖励。`],
    BATTLE_PLAYER_ELIMINATED: ['☠', '幸存者淘汰', `${targetName || actorName} 因 ${event.payload?.reason || '生存失败'} 被淘汰。`],
    BATTLE_FINISHED: ['🏆', '大逃杀结束', `${event.payload?.winnerName || targetName || '最终幸存者'} 获胜，生存评分 ${event.payload?.score ?? '-'}，共淘汰 ${event.payload?.kills ?? 0} 人。`]
  };
  const presentation = descriptions[event.eventType] || ['●', event.eventType, `${actorName} 完成了一次游戏行动。`];
  return presentation;
}

function werewolfRoleLabel(role) {
  return ({ WEREWOLF: '狼人', SEER: '预言家', WITCH: '女巫', GUARD: '守卫', VILLAGER: '平民' })[role] || role || '未知身份';
}

function werewolfPhaseLabel(phase) {
  return ({ NIGHT_WOLF: '狼人行动', NIGHT_GUARD: '守卫守护', NIGHT_SEER: '预言家查验', NIGHT_WITCH: '女巫用药', DAY_DISCUSSION: '白天发言', DAY_VOTE: '放逐投票', FINISHED: '对局结束' })[phase] || phase || '等待阶段';
}

function gameModeLabel(mode) {
  return mode === 'BATTLE_ROYALE' ? 'AI 大逃杀' : mode === 'WEREWOLF' ? 'AI 狼人杀' : mode;
}

function gameStatusLabel(status) {
  return ({ WAITING: '等待开始', RUNNING: '对局进行中', FINISHED: '对局已结束', PAUSED: '已暂停', FAILED: '运行失败' })[status] || status;
}

function decisionSourceLabel(source) {
  return source === 'MODEL' ? '模型决策' : source === 'MODEL_FALLBACK' ? '模型纠错策略' : '本地策略';
}

let selectedArenaPlayerId = null;
let arenaTimelineCollapsed = window.matchMedia?.('(max-width: 1180px)').matches ?? false;
let lastArenaGameId = null;

function samePlayerId(left, right) {
  return left != null && right != null && String(left) === String(right);
}

function findArenaPlayer(players, playerId) {
  return players.find((player) => samePlayerId(player.id, playerId));
}

function latestCompletedGroup(eventGroups, predicate = () => true) {
  return [...eventGroups].reverse().find((group) => group.primary && predicate(group.primary, group)) || null;
}

function renderWerewolfArena(players, game, eventGroups, publicView = false) {
  const phaseOrder = ['NIGHT_WOLF', 'NIGHT_GUARD', 'NIGHT_SEER', 'NIGHT_WITCH', 'DAY_DISCUSSION', 'DAY_VOTE'];
  const currentPhaseIndex = phaseOrder.indexOf(game.currentPhase);
  const currentRound = Number(game.currentRound || 1);
  const latestGroup = latestCompletedGroup(eventGroups);
  const latestEvent = latestGroup?.primary || null;
  const latestInfo = latestEvent ? describeGameEvent(latestGroup, players) : ['◌', '等待首个行动', '推进对局后，智能体的发言、投票和技能行动会直接出现在圆桌上。'];
  const activeActorId = latestEvent?.actorPlayerId;
  const targetId = latestEvent?.payload?.targetPlayerId ?? latestEvent?.payload?.playerId ?? latestGroup?.decision?.payload?.targetPlayerId;
  const latestSpeech = latestCompletedGroup(eventGroups, (event) => event.eventType === 'PLAYER_SPOKE' && Number(event.roundNo || 0) === currentRound)?.primary;
  const speechPlayerId = latestSpeech?.actorPlayerId;
  const isNight = String(game.currentPhase || '').startsWith('NIGHT_');
  const hasKnownRoles = players.length > 0 && players.every((player) => Boolean(player.roleCode));
  const latestByPlayer = new Map();
  [...eventGroups].reverse().forEach((group) => {
    const event = group.primary || group.decision;
    if (event?.actorPlayerId && !latestByPlayer.has(String(event.actorPlayerId))) latestByPlayer.set(String(event.actorPlayerId), event);
  });

  const seatPoints = new Map();
  players.forEach((player, index) => {
    const angle = -90 + (360 / Math.max(players.length, 1)) * index;
    const radians = angle * Math.PI / 180;
    seatPoints.set(String(player.id), {
      left: 50 + Math.cos(radians) * 41,
      top: 54 + Math.sin(radians) * 34,
      x: 500 + Math.cos(radians) * 410,
      y: 389 + Math.sin(radians) * 245
    });
  });

  const votesByActor = new Map();
  eventGroups.forEach((group) => {
    const event = group.primary;
    if (event?.eventType === 'PLAYER_VOTED' && Number(event.roundNo || 0) === currentRound) {
      votesByActor.set(String(event.actorPlayerId), event.payload?.targetPlayerId);
    }
  });
  const voteCounts = new Map();
  votesByActor.forEach((voteTargetId) => {
    if (voteTargetId != null) voteCounts.set(String(voteTargetId), (voteCounts.get(String(voteTargetId)) || 0) + 1);
  });
  const voteLines = game.currentPhase === 'DAY_VOTE'
    ? [...votesByActor.entries()].map(([actorId, voteTargetId]) => {
        const from = seatPoints.get(actorId);
        const to = seatPoints.get(String(voteTargetId));
        if (!from || !to) return '';
        return `<line class="wolf-vote-line" x1="${from.x}" y1="${from.y}" x2="${to.x}" y2="${to.y}" marker-end="url(#wolf-vote-arrow)" />`;
      }).join('')
    : '';
  const actionFrom = seatPoints.get(String(activeActorId));
  const actionTo = seatPoints.get(String(targetId));
  const actionLine = latestEvent?.eventType !== 'PLAYER_VOTED' && actionFrom && actionTo
    ? `<line class="wolf-action-line" x1="${actionFrom.x}" y1="${actionFrom.y}" x2="${actionTo.x}" y2="${actionTo.y}" marker-end="url(#wolf-action-arrow)" />`
    : '';

  const seats = players.map((player, index) => {
    const point = seatPoints.get(String(player.id));
    const roleCamp = !player.roleCode ? 'unknown' : player.roleCode === 'WEREWOLF' ? 'wolf' : player.roleCode === 'VILLAGER' ? 'villager' : 'god';
    const latest = latestByPlayer.get(String(player.id));
    const roleState = player.roleCode === 'WITCH'
      ? `解药 ${player.antidoteAvailable ? '✓' : '×'} · 毒药 ${player.poisonAvailable ? '✓' : '×'}`
      : player.roleCode === 'SEER' ? `已查验 ${Object.keys(player.seerChecks || {}).length} 人`
        : latest ? describeGameEvent({ primary: latest, decision: null }, players)[1] : '等待行动';
    const speaking = samePlayerId(player.id, speechPlayerId);
    const active = samePlayerId(player.id, activeActorId);
    const targeted = samePlayerId(player.id, targetId);
    const selected = samePlayerId(player.id, selectedArenaPlayerId);
    const voteCount = voteCounts.get(String(player.id)) || 0;
    const speechVerticalClass = point.top < 42 ? 'speech-below' : 'speech-above';
    const speechHorizontalClass = point.left < 20 ? 'speech-align-left' : point.left > 80 ? 'speech-align-right' : '';
    return `<button type="button" class="wolf-seat role-${roleCamp} ${player.status === 'ALIVE' ? 'alive' : 'eliminated'} ${active ? 'is-acting' : ''} ${targeted ? 'is-targeted' : ''} ${speaking ? 'is-speaking' : ''} ${selected ? 'is-selected' : ''}" style="left:${point.left}%;top:${point.top}%" data-arena-player="${Number(player.id)}" aria-label="聚焦 ${escapeHtml(player.displayName)}">
      <i>${Number(player.seatNo)}</i><span class="wolf-seat-copy"><b>${escapeHtml(player.displayName)}</b><span>${escapeHtml(werewolfRoleLabel(player.roleCode))}</span><small>${escapeHtml(roleState)}</small></span>
      <em>${player.status === 'ALIVE' ? active ? '行动中' : '存活' : '已出局'}</em>
      ${voteCount ? `<span class="wolf-vote-count">${voteCount} 票</span>` : ''}
      ${speaking ? `<span class="wolf-seat-speech ${speechVerticalClass} ${speechHorizontalClass}"><small>正在发言</small>“${escapeHtml(latestSpeech.payload?.message || '…')}”</span>` : ''}
    </button>`;
  }).join('');
  const aliveWolves = players.filter((player) => player.status === 'ALIVE' && player.roleCode === 'WEREWOLF').length;
  const aliveGood = players.filter((player) => player.status === 'ALIVE' && player.roleCode && player.roleCode !== 'WEREWOLF').length;
  const aliveCount = players.filter((player) => player.status === 'ALIVE').length;
  const phaseSteps = phaseOrder.map((phase, index) => `<span class="${index < currentPhaseIndex ? 'done' : index === currentPhaseIndex ? 'active' : ''}"><i></i>${werewolfPhaseLabel(phase)}</span>`).join('');
  const voteBoard = [...voteCounts.entries()]
    .sort((left, right) => right[1] - left[1])
    .slice(0, 3)
    .map(([playerId, count]) => {
      const player = findArenaPlayer(players, playerId);
      return player ? `<span><b>${Number(player.seatNo)}号</b>${escapeHtml(player.displayName)}<em>${count} 票</em></span>` : '';
    }).join('');
  return `<div class="werewolf-arena ${isNight ? 'night-scene' : 'day-scene'}">
    <div class="wolf-sky"><span>${isNight ? '☾' : '☀'}</span><small>${isNight ? '夜幕笼罩，私密行动只对授权视角可见' : '白昼议事，发言与投票公开进行'}</small></div>
    <div class="wolf-phase-track">${phaseSteps}</div>
    <svg class="wolf-links" viewBox="0 0 1000 720" preserveAspectRatio="none" aria-hidden="true"><defs><marker id="wolf-vote-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z" /></marker><marker id="wolf-action-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z" /></marker></defs>${voteLines}${actionLine}</svg>
    <div class="wolf-table"><div class="wolf-table-ring"></div><div class="wolf-center" aria-live="polite"><small>第 ${currentRound} 轮 · ${escapeHtml(latestInfo[0])} 现场</small><strong>${escapeHtml(werewolfPhaseLabel(game.currentPhase))}</strong><b class="wolf-live-title">${escapeHtml(latestInfo[1])}</b><p>${escapeHtml(latestInfo[2])}</p><span>${aliveCount} 人存活</span><div>${hasKnownRoles ? `<b class="good-count">好人 ${aliveGood}</b><b class="wolf-count">狼人 ${aliveWolves}</b>` : '<b class="unknown-count">阵营人数已隐藏</b>'}</div>${voteBoard ? `<div class="wolf-vote-board">${voteBoard}</div>` : ''}</div></div>
    ${seats}
    <div class="god-view-note">${publicView && game.status !== 'FINISHED' ? '公开观战 · 身份与私有技能已隐藏' : '房主视角 · 身份与私有技能状态可见'} · 点击席位可聚焦</div>
  </div>`;
}

function renderWerewolfEventCard(group, event, players, index) {
  const info = describeGameEvent(group, players);
  const actor = players.find((player) => player.id === event.actorPlayerId);
  const decision = group.decision?.payload || {};
  const targetId = event.payload?.targetPlayerId || decision.targetPlayerId || event.payload?.playerId;
  const target = players.find((player) => player.id === targetId);
  const message = event.eventType === 'PLAYER_SPOKE' ? event.payload?.message : null;
  return `<article class="werewolf-event-card ${index === 0 ? 'latest' : ''}">
    <header><i>${escapeHtml(info[0])}</i><div><small>${actor ? `${Number(actor.seatNo)}号 · ${escapeHtml(actor.displayName)}` : '系统事件'}</small><b>${escapeHtml(info[1])}</b></div><em>#${Number(event.sequenceNo || 0)}</em></header>
    ${message ? `<blockquote>“${escapeHtml(message)}”</blockquote>` : `<p>${escapeHtml(info[2])}${target ? ` 目标：${target.seatNo}号 ${escapeHtml(target.displayName)}` : ''}</p>`}
    ${group.decision ? `<div class="wolf-decision"><span>决策依据</span><p>${escapeHtml(decision.summary || '未提供策略说明')}</p><em>${escapeHtml(decisionSourceLabel(decision.source))} · 模型自评置信度 ${decision.confidence || 0}%（非胜率）</em></div>` : ''}
    <footer><span>第 ${Number(event.roundNo || 0)} 轮</span><span>${escapeHtml(werewolfPhaseLabel(event.phase))}</span>${group.phaseChangedTo ? `<b>→ ${escapeHtml(werewolfPhaseLabel(group.phaseChangedTo))}</b>` : ''}</footer>
  </article>`;
}

function battleActionLabel(action) {
  const labels = { MOVE: '移动', SEARCH: '搜索物资', REST: '休整', HIDE: '隐蔽', HEAL: '使用医疗包', GUARD: '加固防具', TRADE: '交易', ATTACK: '发起攻击', SKIP: '暂不行动' };
  return labels[action] || action || '一次行动';
}

function battleLootLabel(type, amount, name) {
  const labels = { WEAPON: name || '武器', WEAPON_UPGRADE: `${name || '武器'}升级`, AMMO: `弹药 +${amount}`, FOOD: `食物 +${amount}`, WATER: `水 +${amount}`, MEDKIT: `医疗包 +${amount}`, ARMOR: `护甲 +${amount}` };
  return labels[type] || '未知物资';
}

function battleActionResult(payload, target) {
  if (payload.action === 'ATTACK' && target) return `攻击了 ${target.displayName}，造成 ${payload.damage ?? 0} 点伤害${payload.critical ? '（暴击）' : ''}，对方生命降至 ${payload.targetHealth ?? '-'}。`;
  if (payload.action === 'SEARCH') return `${payload.supplyDrop ? '搜刮空投：' : '搜索成功：'}获得${battleLootLabel(payload.lootType, payload.lootAmount, payload.lootName)}。`;
  if (payload.action === 'MOVE') return `已移动至 ${payload.region || '新区域'}。`;
  if (payload.action === 'HEAL') return `消耗医疗包恢复生命，当前生命 ${payload.health ?? '-'}。`;
  if (payload.action === 'GUARD') return `完成防御准备，当前护甲 ${payload.armor ?? 0}。`;
  if (payload.action === 'REST') return `原地休整恢复状态，当前生命 ${payload.health ?? '-'}。`;
  if (payload.action === 'HIDE') return '进入隐蔽状态，遭受攻击时可降低伤害。';
  if (payload.action === 'TRADE' && target) return `与 ${target.displayName} 完成了一次物资交易。`;
  if (payload.action === 'SKIP') return '保持观察，没有执行主动行动。';
  return '行动已生效。';
}

function renderBattleActionCard(group, event, players, index) {
  const actor = players.find((player) => player.id === event.actorPlayerId);
  const payload = event.payload || {};
  const actorName = actor?.displayName || `玩家 #${event.actorPlayerId}`;
  const action = battleActionLabel(payload.action);
  const target = players.find((player) => player.id === payload.targetPlayerId);
  const result = battleActionResult(payload, target);
  const decision = group.decision?.payload || {};
  return `<article class="battle-action-card ${index === 0 ? 'latest' : ''}">
    <header><span class="action-round">第 ${Number(event.roundNo || 0)} 轮</span><span class="action-phase">${escapeHtml(event.phase || '行动阶段')}</span><em>#${Number(event.sequenceNo || 0)}${group.endSequence !== event.sequenceNo ? `–${Number(group.endSequence || 0)}` : ''}</em></header>
    <div class="battle-action-main"><i>⚔</i><div><small>${escapeHtml(actorName)} 的行动</small><h3>${escapeHtml(action)}</h3><p>${escapeHtml(result)}</p></div></div>
    <div class="survival-stats"><span><i>♥</i>生命 <b>${payload.health ?? '-'}</b></span><span><i>◈</i>食物 <b>${payload.food ?? '-'}</b></span><span><i>◌</i>水 <b>${payload.water ?? '-'}</b></span><span><i>⬡</i>护甲 <b>${payload.armor ?? 0}</b></span><span><i>✚</i>医疗 <b>${payload.medkits ?? 0}</b></span>${payload.armed ? `<span class="armed"><i>⚔</i>${escapeHtml(payload.weaponName || '武器')} Lv.${payload.weaponLevel || 1} · ${payload.ammo ?? 0} 发</span>` : ''}<span><i>☠</i>淘汰 <b>${payload.kills ?? 0}</b></span></div>
    ${group.decision ? `<div class="decision-reason"><span>选择理由</span><p>${escapeHtml(decision.summary || '未提供策略说明')}</p><em>${escapeHtml(decisionSourceLabel(decision.source))} · 模型自评置信度 ${decision.confidence || 0}%（非胜率）</em></div>` : ''}
  </article>`;
}

function renderBattleMap(players, game, eventGroups) {
  const regionMeta = {
    A: ['中央废墟', '⌂', '武器 / 弹药'],
    B: ['北部密林', '♣', '食物 / 医疗'],
    C: ['东岸码头', '≋', '饮水 / 弹药'],
    D: ['南部工事', '◇', '护甲 / 弹药'],
    E: ['西侧营地', '△', '医疗 / 补给']
  };
  const ringStage = Math.min(5, 1 + Math.floor(Math.max(0, (game.currentRound || 1) - 1) / 3));
  const ringOrder = ['A', 'B', 'C', 'E', 'D'];
  const safeRegions = ringOrder.slice(0, 6 - ringStage);
  const nextRemovedRegion = ringStage < 5 ? safeRegions[safeRegions.length - 1] : null;
  const supplyEvent = [...eventGroups].reverse().map((group) => group.primary || group.decision).find((event) => event?.eventType === 'BATTLE_SUPPLY_DROP');
  const supplyDropRegion = supplyEvent?.payload?.region || null;
  const latestGroup = latestCompletedGroup(eventGroups);
  const latestEvent = latestGroup?.primary || null;
  const latestInfo = latestEvent ? describeGameEvent(latestGroup, players) : ['◌', '等待首个行动', '推进对局后，移动、交战、搜索与毒圈变化会直接显示在地图上。'];
  const activeActorId = latestEvent?.actorPlayerId;
  const targetId = latestEvent?.payload?.targetPlayerId ?? latestEvent?.payload?.playerId;
  const actor = findArenaPlayer(players, activeActorId);
  const target = findArenaPlayer(players, targetId);
  const action = latestEvent?.eventType === 'BATTLE_ACTION_EXECUTED' ? latestEvent.payload?.action : latestEvent?.eventType;
  const actionGlyphs = { MOVE: '➜', SEARCH: '⌕', REST: '♥', HIDE: '◐', HEAL: '✚', GUARD: '⬡', TRADE: '⇄', ATTACK: '⚔', SKIP: '◌', BATTLE_ZONE_UPDATED: '◎', BATTLE_ZONE_DAMAGE: '☣', BATTLE_SUPPLY_DROP: '▣', BATTLE_PLAYER_ELIMINATED: '☠', BATTLE_FINISHED: '🏆' };
  const actionGlyph = actionGlyphs[action] || latestInfo[0];
  const actionTitle = latestEvent?.eventType === 'BATTLE_ACTION_EXECUTED' && actor
    ? `${actor.displayName} · ${battleActionLabel(action)}`
    : latestInfo[1];
  const actionDetail = latestEvent?.eventType === 'BATTLE_ACTION_EXECUTED'
    ? battleActionResult(latestEvent.payload || {}, target)
    : latestInfo[2];
  const regionPoints = {
    A: { x: 500, y: 355 }, B: { x: 195, y: 112 }, C: { x: 805, y: 112 },
    D: { x: 805, y: 594 }, E: { x: 195, y: 594 }
  };
  const actorRegion = actor?.region || latestEvent?.payload?.region || null;
  const targetRegion = target?.region || null;
  let sourceRegion = actorRegion;
  if (action === 'MOVE' && latestEvent) {
    const previousAction = [...eventGroups].reverse().map((group) => group.primary).find((event) => event
      && event.eventType === 'BATTLE_ACTION_EXECUTED'
      && samePlayerId(event.actorPlayerId, activeActorId)
      && Number(event.sequenceNo || 0) < Number(latestEvent.sequenceNo || 0)
      && event.payload?.region);
    sourceRegion = previousAction?.payload?.region || null;
  }
  const traceStart = regionPoints[action === 'MOVE' ? sourceRegion : actorRegion];
  const traceEnd = regionPoints[action === 'MOVE' ? actorRegion : targetRegion];
  let actionTrace = '';
  if (traceStart && traceEnd && (action === 'MOVE' || action === 'ATTACK' || action === 'TRADE')) {
    if (traceStart.x === traceEnd.x && traceStart.y === traceEnd.y) {
      actionTrace = `<circle class="battle-combat-pulse" cx="${traceStart.x}" cy="${traceStart.y}" r="42" /><circle class="battle-combat-pulse delayed" cx="${traceStart.x}" cy="${traceStart.y}" r="64" />`;
    } else {
      const controlX = (traceStart.x + traceEnd.x) / 2;
      const controlY = Math.min(traceStart.y, traceEnd.y) - 55;
      actionTrace = `<path class="battle-action-trace action-${String(action || '').toLowerCase()}" d="M ${traceStart.x} ${traceStart.y} Q ${controlX} ${controlY} ${traceEnd.x} ${traceEnd.y}" marker-end="url(#battle-trace-arrow)" />`;
    }
  } else if (regionPoints[actorRegion]) {
    actionTrace = `<circle class="battle-action-ping" cx="${regionPoints[actorRegion].x}" cy="${regionPoints[actorRegion].y}" r="34" />`;
  }
  const hiddenPlayers = players.filter((player) => player.status === 'ALIVE' && !player.region);
  const zones = Object.entries(regionMeta).map(([region, meta]) => {
    const occupants = players.filter((player) => player.region === region);
    const playerMarkers = occupants.map((player) => {
      const alive = player.status === 'ALIVE';
      const active = samePlayerId(player.id, activeActorId);
      const targeted = samePlayerId(player.id, targetId);
      const selected = samePlayerId(player.id, selectedArenaPlayerId);
      const health = Math.max(0, Math.min(100, Number(player.health ?? 0)));
      return `<button type="button" class="map-player ${alive ? 'alive' : 'eliminated'} ${player.hidden ? 'hidden' : ''} ${active ? 'is-acting' : ''} ${targeted ? 'is-targeted' : ''} ${selected ? 'is-selected' : ''} ${health <= 30 && alive ? 'is-critical' : ''}" data-arena-player="${Number(player.id)}" aria-label="聚焦 ${escapeHtml(player.displayName)}">
        <span class="map-player-token"><i>${Number(player.seatNo)}</i>${active ? `<em>${escapeHtml(actionGlyph)}</em>` : ''}</span><span class="map-player-copy"><b>${escapeHtml(player.displayName)}</b><small>${player.hidden ? '隐蔽中' : player.weaponName ? `${escapeHtml(player.weaponName)} Lv.${Number(player.weaponLevel || 1)} · ${Number(player.ammo || 0)} 发` : '徒手探索'}</small><span class="map-health-track"><i style="width:${health}%"></i></span></span>
        <div class="map-player-vitals"><span>♥ ${player.health ?? '-'}</span><span>⬡ ${player.armor ?? 0}</span><span>◆ ${player.food ?? '-'}</span><span>● ${player.water ?? '-'}</span>${player.medkits ? `<span>✚ ${player.medkits}</span>` : ''}<span>☠ ${player.kills ?? 0}</span></div>
      </button>`;
    }).join('');
    const safe = safeRegions.includes(region);
    return `<section class="map-zone zone-${region.toLowerCase()} ${safe ? 'safe-zone' : 'storm-zone'} ${region === nextRemovedRegion ? 'closing-soon' : ''} ${region === supplyDropRegion ? 'supply-zone' : ''} ${region === actorRegion ? 'active-zone' : ''} ${region === targetRegion ? 'target-zone' : ''}">
      ${region === supplyDropRegion ? '<span class="supply-badge"><i>▣</i> 空投区域</span>' : ''}<header><i>${meta[1]}</i><div><b>${region}区 · ${meta[0]}</b><small>${safe ? (region === nextRemovedRegion ? '下次缩圈移除' : '安全区') : '毒圈区域'} · ${occupants.filter((player) => player.status === 'ALIVE').length} 人</small><em>高概率：${meta[2]}</em></div></header>
      <div class="zone-players">${playerMarkers || '<span class="zone-empty">暂无玩家</span>'}</div>
    </section>`;
  }).join('');
  const aliveCount = players.filter((player) => player.status === 'ALIVE').length;
  const armedCount = players.filter((player) => player.status === 'ALIVE' && player.armed).length;
  return `<div class="battle-map ring-stage-${ringStage}" style="--ring-pressure:${ringStage * 8}px">
    <div class="map-grid-lines"></div>
    <div class="battle-storm-vignette"></div>
    <svg class="battle-action-layer" viewBox="0 0 1000 720" preserveAspectRatio="none" aria-hidden="true"><defs><marker id="battle-trace-arrow" markerWidth="9" markerHeight="9" refX="8" refY="4.5" orient="auto"><path d="M0,0 L9,4.5 L0,9 Z" /></marker></defs>${actionTrace}</svg>
    <div class="map-ring-status"><span>安全区监测 · ${ringStage}/5</span><b>${aliveCount} 人存活</b><div class="ring-meter">${Array.from({ length: 5 }, (_, index) => `<i class="${index < ringStage ? 'active' : ''}"></i>`).join('')}</div><small>${ringStage < 5 ? `第 ${ringStage * 3 + 1} 轮再次缩圈` : '最终安全区'} · ${armedCount} 人持械</small></div>
    <div class="battle-live-feed action-${String(action || 'waiting').toLowerCase()}" aria-live="polite"><i>${escapeHtml(actionGlyph)}</i><div><small>第 ${Number(latestEvent?.roundNo || game.currentRound || 1)} 轮 · #${Number(latestEvent?.sequenceNo || 0)}</small><b>${escapeHtml(actionTitle)}</b><p>${escapeHtml(actionDetail)}</p></div></div>
    ${zones}
    ${hiddenPlayers.length ? `<section class="map-zone hidden-zone"><header><i>?</i><div><b>隐蔽区域</b><small>${hiddenPlayers.length} 名玩家位置已隐藏</small></div></header><div class="zone-players">${hiddenPlayers.map((player) => `<button type="button" class="map-player alive hidden ${samePlayerId(player.id, selectedArenaPlayerId) ? 'is-selected' : ''}" data-arena-player="${Number(player.id)}"><span class="map-player-token"><i>${Number(player.seatNo)}</i></span><span class="map-player-copy"><b>${escapeHtml(player.displayName)}</b><small>位置保密</small></span></button>`).join('')}</div></section>` : ''}
    <div class="map-legend"><span><i class="alive-dot"></i>存活</span><span><i class="safe-dot"></i>安全区</span><span><i class="closing-dot"></i>即将缩圈</span><span><i class="supply-dot"></i>空投</span><span>点击玩家可聚焦</span></div>
  </div>`;
}

let autoPlayEnabled = false;
let autoPlayTimer = null;
let autoPlayDelay = 150;
let autoPlaySteps = 0;
const AUTO_PLAY_STEP_LIMIT = 200;

function stopAutoPlay() {
  autoPlayEnabled = false;
  clearTimeout(autoPlayTimer);
  autoPlayTimer = null;
}

async function runAutoStep() {
  if (!autoPlayEnabled) return;
  if (autoPlaySteps >= AUTO_PLAY_STEP_LIMIT) {
    stopAutoPlay();
    showToast('自动运行已达到 200 步安全上限，请检查对局状态');
    return;
  }
  const continued = await advanceCurrentGame(true);
  if (continued) autoPlaySteps += 1;
  if (!continued || !autoPlayEnabled) return stopAutoPlay();
  autoPlayTimer = setTimeout(runAutoStep, autoPlayDelay);
}

function toggleAutoPlay() {
  autoPlayEnabled = !autoPlayEnabled;
  if (autoPlayEnabled) autoPlaySteps = 0;
  clearTimeout(autoPlayTimer);
  if (autoPlayEnabled) runAutoStep();
  else loadCurrentGame();
}

function renderRealWatch(game, players, events, publicView = false) {
  const watchPage = document.getElementById('watch-page');
  if (!watchPage) return;
  if (!samePlayerId(lastArenaGameId, game.id)) {
    selectedArenaPlayerId = null;
    lastArenaGameId = game.id;
  }
  const mockShell = watchPage.querySelector('.watch-shell');
  if (mockShell) mockShell.style.display = 'none';
  let dashboard = watchPage.querySelector('.real-watch-dashboard');
  if (!dashboard) {
    dashboard = document.createElement('div');
    dashboard.className = 'real-watch-dashboard';
    watchPage.appendChild(dashboard);
  }
  const eventGroups = groupGameEvents(events);
  const recentEvents = eventGroups.slice(-10).reverse();
  const isBattle = game.mode === 'BATTLE_ROYALE';
  const gameStage = isBattle
    ? renderBattleMap(players, game, eventGroups)
    : renderWerewolfArena(players, game, eventGroups, publicView);
  dashboard.innerHTML = `
    <header class="real-watch-header">
      <button class="back-button real-back">←</button>
      <div><span class="status-live"><i></i> ENGINE LIVE</span><h1>${escapeHtml(game.name)} · #${Number(game.id)}</h1><p>${escapeHtml(gameModeLabel(game.mode))} · 第 ${Number(game.currentRound || 1)} 轮 · ${isBattle ? '生存行动' : escapeHtml(werewolfPhaseLabel(game.currentPhase))}</p></div>
      <div class="real-watch-actions"><b>${escapeHtml(gameStatusLabel(game.status))}</b><button class="secondary real-timeline-toggle" type="button" aria-expanded="${!arenaTimelineCollapsed}" aria-controls="arena-event-panel">${arenaTimelineCollapsed ? '展开战报' : '收起战报'}</button>${game.publicGame ? '<button class="secondary real-share" type="button">复制公开链接</button>' : ''}${publicView ? '<span>公开只读观战</span>' : `<select class="auto-speed" aria-label="自动运行速度"><option value="800" ${autoPlayDelay === 800 ? 'selected' : ''}>慢速观察</option><option value="150" ${autoPlayDelay === 150 ? 'selected' : ''}>标准速度</option><option value="0" ${autoPlayDelay === 0 ? 'selected' : ''}>快速模拟</option></select><button class="secondary real-advance" ${game.status === 'FINISHED' || autoPlayEnabled ? 'disabled' : ''}>推进一步</button><button class="primary real-auto" ${game.status === 'FINISHED' ? 'disabled' : ''}>${autoPlayEnabled ? '暂停模拟' : '自动模拟'}</button>`}</div>
    </header>
    <div class="real-watch-grid ${arenaTimelineCollapsed ? 'timeline-collapsed' : ''}">
      <section class="real-player-panel arena-board ${isBattle ? 'battle-board' : 'werewolf-board'}"><div class="real-section-head"><div><small class="arena-kicker">LIVE ARENA</small><h2>${isBattle ? '生存区域地图' : '狼人杀推理圆桌'}</h2></div><span>${players.length} 名玩家 · ${isBattle ? '移动、交战、毒圈与物资实时可视化' : '发言、投票与阶段行动实时可视化'}</span></div>${gameStage}</section>
      <aside class="real-event-panel" id="arena-event-panel"><div class="real-section-head"><div><small class="arena-kicker">BATTLE REPORT</small><h2>详细战报</h2></div><span>${eventGroups.length} 次行动 · ${events.length} 条底层事件</span></div><div class="real-event-list">
        ${recentEvents.length ? recentEvents.map((group, index) => { const event = group.primary || group.decision; if (isBattle && event.eventType === 'BATTLE_ACTION_EXECUTED') return renderBattleActionCard(group, event, players, index); if (!isBattle) return renderWerewolfEventCard(group, event, players, index); const info = describeGameEvent(group, players); return `<article class="action-event-card ${index === 0 ? 'latest' : ''}"><i>${escapeHtml(info[0])}</i><div><b>${escapeHtml(info[1])}</b><p>${escapeHtml(info[2])}</p><span>第 ${Number(event.roundNo || 0)} 轮 · ${escapeHtml(event.phase || '-')}</span></div><em>#${Number(event.sequenceNo || 0)}</em></article>`; }).join('') : '<div class="real-empty">暂无事件，点击“自动运行”调用游戏引擎。</div>'}
      </div></aside>
    </div>`;
  dashboard.querySelector('.real-back')?.addEventListener('click', () => goTo('home'));
  dashboard.querySelector('.real-advance')?.addEventListener('click', advanceCurrentGame);
  dashboard.querySelector('.real-auto')?.addEventListener('click', toggleAutoPlay);
  dashboard.querySelector('.real-share')?.addEventListener('click', shareCurrentGame);
  dashboard.querySelector('.auto-speed')?.addEventListener('change', (event) => { autoPlayDelay = Number(event.target.value); });
  dashboard.querySelector('.real-timeline-toggle')?.addEventListener('click', () => {
    arenaTimelineCollapsed = !arenaTimelineCollapsed;
    renderRealWatch(game, players, events, publicView);
  });
  dashboard.querySelectorAll('[data-arena-player]').forEach((playerButton) => {
    playerButton.addEventListener('click', () => {
      const playerId = Number(playerButton.dataset.arenaPlayer);
      selectedArenaPlayerId = samePlayerId(selectedArenaPlayerId, playerId) ? null : playerId;
      renderRealWatch(game, players, events, publicView);
    });
  });
}

async function advanceCurrentGame(fromAutoPlay = false) {
  const session = getGameSession();
  if (!session) { showToast('请先创建一场对局'); return false; }
  if (session.publicView) { showToast('公开观战为只读模式'); return false; }
  const actionButtons = [document.querySelector('.real-advance'), document.querySelector('.real-auto')].filter(Boolean);
  actionButtons.forEach((button) => { button.disabled = true; button.textContent = '模型行动中…'; });
  try {
    const path = session.mode === 'WEREWOLF' ? 'next-turn' : 'battle-next-turn';
    await apiRequest(`/api/games/${session.gameId}/${path}`, { method: 'POST' });
    const game = await loadCurrentGame(true);
    if (game?.status === 'FINISHED') { stopAutoPlay(); return false; }
    return true;
  } catch (error) {
    if (fromAutoPlay) stopAutoPlay();
    showToast(`推进失败：${error.message}`);
    return false;
  } finally {
    actionButtons.forEach((button) => { button.disabled = false; if (button.textContent === '模型行动中…') button.textContent = '推进对局'; });
  }
}

async function ensureVisitorSession() {
  const stored = localStorage.getItem('agentArenaUser');
  if (stored) {
    let session = null;
    try {
      session = JSON.parse(stored);
      if (session?.userId && session?.visitorToken) {
        const profile = await apiRequest('/api/users/me');
        if (Number(profile?.userId) === Number(session.userId)) {
          const restored = { ...session, ...profile, visitorToken: session.visitorToken };
          localStorage.setItem('agentArenaUser', JSON.stringify(restored));
          renderUserIdentity(restored);
          return restored;
        }
      }
    } catch (error) {
      if (session?.visitorToken && error?.status !== 401 && error?.code !== 'A0230') {
        renderUserIdentity(session);
        throw error;
      }
      /* Recreate only a damaged or explicitly expired session below. */
    }
    localStorage.removeItem('agentArenaUser');
  }
  const visitor = await apiRequest('/api/users/visitor', {
    method: 'POST',
    body: JSON.stringify({ nickname: 'Arena 玩家' })
  });
  localStorage.setItem('agentArenaUser', JSON.stringify(visitor));
  renderUserIdentity(visitor);
  return visitor;
}

function readStoredUser() {
  try { return JSON.parse(localStorage.getItem('agentArenaUser') || 'null'); }
  catch { return null; }
}

function renderUserIdentity(user) {
  const avatar = document.querySelector('.avatar');
  const accountName = document.getElementById('account-name');
  const accountStatus = document.getElementById('account-status');
  const accountTrigger = document.getElementById('account-trigger');
  const nickname = String(user?.nickname || 'Arena 玩家');
  const registered = Boolean(user?.registered || user?.username);
  if (avatar) {
    avatar.textContent = nickname.slice(0, 2).toUpperCase();
    avatar.title = nickname;
  }
  if (accountName) accountName.textContent = registered ? nickname : '游客模式';
  if (accountStatus) accountStatus.textContent = registered ? `@${user.username}` : '登录 / 注册';
  if (accountTrigger) accountTrigger.setAttribute('aria-label', registered ? `账号：${nickname}` : `当前游客：${nickname}，点击登录或注册`);
}

function switchAuthTab(tab) {
  document.querySelectorAll('[data-auth-tab]').forEach((button) => button.classList.toggle('active', button.dataset.authTab === tab));
  document.querySelectorAll('[data-auth-form]').forEach((form) => form.classList.toggle('active', form.dataset.authForm === tab));
  document.getElementById(`${tab}-username`)?.focus();
}

function openAuthModal(tab = 'login') {
  const modal = document.getElementById('auth-modal');
  const guestPanel = document.getElementById('auth-guest-panel');
  const accountPanel = document.getElementById('auth-account-panel');
  const user = readStoredUser();
  const registered = Boolean(user?.registered || user?.username);
  if (!modal) return;
  modal.hidden = false;
  document.body.classList.add('auth-open');
  if (guestPanel) guestPanel.hidden = registered;
  if (accountPanel) accountPanel.hidden = !registered;
  if (registered) {
    const nickname = String(user.nickname || user.username);
    document.getElementById('account-panel-avatar').textContent = nickname.slice(0, 2).toUpperCase();
    document.getElementById('account-panel-name').textContent = nickname;
    document.getElementById('account-panel-username').textContent = `@${user.username}`;
    document.getElementById('logout-button')?.focus();
  } else {
    switchAuthTab(tab);
  }
}

function closeAuthModal() {
  const modal = document.getElementById('auth-modal');
  if (modal) modal.hidden = true;
  document.body.classList.remove('auth-open');
  if (window.location.hash === '#login' || window.location.hash === '#register') {
    history.replaceState(null, '', window.location.pathname + window.location.search);
  }
}

function saveAuthSession(session, message) {
  const previous = readStoredUser();
  if (previous?.userId && Number(previous.userId) !== Number(session.userId)) {
    localStorage.removeItem('agentArenaSession');
  }
  localStorage.setItem('agentArenaUser', JSON.stringify(session));
  renderUserIdentity(session);
  closeAuthModal();
  showToast(message);
}

document.getElementById('account-trigger')?.addEventListener('click', () => openAuthModal());
document.getElementById('auth-close')?.addEventListener('click', closeAuthModal);
document.getElementById('auth-modal')?.addEventListener('click', (event) => {
  if (event.target.id === 'auth-modal') closeAuthModal();
});
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape' && !document.getElementById('auth-modal')?.hidden) closeAuthModal();
});
document.querySelectorAll('[data-auth-tab]').forEach((button) => button.addEventListener('click', () => switchAuthTab(button.dataset.authTab)));

document.getElementById('login-form')?.addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const button = form.querySelector('button[type="submit"]');
  const errorBox = document.getElementById('login-error');
  button.disabled = true;
  button.textContent = '正在登录…';
  if (errorBox) errorBox.textContent = '';
  try {
    const session = await apiRequest('/api/users/login', {
      method: 'POST',
      body: JSON.stringify({
        username: document.getElementById('login-username').value.trim(),
        password: document.getElementById('login-password').value
      })
    });
    form.reset();
    saveAuthSession(session, `欢迎回来，${session.nickname}`);
    loadLobbyGames();
  } catch (error) {
    if (errorBox) errorBox.textContent = error.message;
  } finally {
    button.disabled = false;
    button.textContent = '登录并继续';
  }
});

document.getElementById('register-form')?.addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const button = form.querySelector('button[type="submit"]');
  const errorBox = document.getElementById('register-error');
  button.disabled = true;
  button.textContent = '正在创建账号…';
  if (errorBox) errorBox.textContent = '';
  try {
    await ensureVisitorSession();
    const session = await apiRequest('/api/users/register', {
      method: 'POST',
      body: JSON.stringify({
        username: document.getElementById('register-username').value.trim(),
        nickname: document.getElementById('register-nickname').value.trim() || null,
        password: document.getElementById('register-password').value
      })
    });
    form.reset();
    saveAuthSession(session, '账号创建成功，当前游客数据已保留');
  } catch (error) {
    if (errorBox) errorBox.textContent = error.message;
  } finally {
    button.disabled = false;
    button.textContent = '注册并保留当前数据';
  }
});

document.getElementById('logout-button')?.addEventListener('click', async (event) => {
  const button = event.currentTarget;
  button.disabled = true;
  button.textContent = '正在退出…';
  try {
    await apiRequest('/api/users/logout', { method: 'POST' });
    localStorage.removeItem('agentArenaUser');
    localStorage.removeItem('agentArenaSession');
    closeAuthModal();
    const visitor = await ensureVisitorSession();
    renderUserIdentity(visitor);
    goTo('home');
    showToast('已安全退出，当前已切换为游客模式');
  } catch (error) {
    showToast(`退出失败：${error.message}`);
  } finally {
    button.disabled = false;
    button.textContent = '退出登录';
  }
});

function openLobbyGame(game) {
  localStorage.setItem('agentArenaSession', JSON.stringify({
    gameId: game.id,
    mode: game.mode
  }));
  history.replaceState(null, '', window.location.pathname + window.location.search);
  goTo(game.status === 'FINISHED' ? 'replays' : 'watch');
}

function renderLobbyGames(games) {
  const grid = document.getElementById('live-game-grid');
  const liveLabel = document.getElementById('lobby-live-count');
  const totalLabel = document.getElementById('lobby-game-count');
  const continueButton = document.getElementById('continue-latest-game');
  const runningCount = games.filter((game) => game.status === 'RUNNING').length;
  if (liveLabel) liveLabel.textContent = runningCount ? `${runningCount} 场真实对局正在进行` : '当前没有运行中的对局';
  if (totalLabel) totalLabel.textContent = `共 ${games.length} 场 · 最近更新优先`;
  if (!grid) return;
  if (!games.length) {
    grid.innerHTML = '<div class="lobby-empty"><span>⌁</span><h3>还没有真实对局</h3><p>创建狼人杀或大逃杀，行动、地图和复盘都会自动记录。</p><button class="primary" type="button" data-create-first>创建第一场对局</button></div>';
    grid.querySelector('[data-create-first]')?.addEventListener('click', () => goTo('create'));
    if (continueButton) {
      continueButton.disabled = false;
      continueButton.onclick = () => goTo('create');
      continueButton.innerHTML = '创建第一场对局 <span>↗</span>';
    }
    return;
  }
  grid.innerHTML = games.slice(0, 9).map((game, index) => {
    const battle = game.mode === 'BATTLE_ROYALE';
    const statusClass = String(game.status || '').toLowerCase();
    const statusText = gameStatusLabel(game.status);
    const phaseText = battle ? `第 ${game.currentRound || 1} 轮 · 生存行动` : `${werewolfPhaseLabel(game.currentPhase)} · 第 ${game.currentRound || 1} 轮`;
    const updated = game.updateTime ? new Date(game.updateTime).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '刚刚更新';
    return `<article class="game-card real-lobby-card ${index === 0 ? 'featured' : ''}" data-lobby-index="${index}" tabindex="0" role="button">
      <div class="card-cover ${battle ? 'island-cover' : 'werewolf-cover'}"><span class="status-live status-${statusClass}"><i></i>${escapeHtml(statusText)}</span><span class="spectators">#${game.id}</span>${battle ? '<div class="map-nodes"><i></i><i></i><i></i><i></i><i></i></div>' : '<div class="cover-symbol">◒</div>'}<div class="round-tag">ROUND ${String(game.currentRound || 1).padStart(2, '0')}</div></div>
      <div class="game-card-body"><div class="game-meta"><span>${battle ? 'AI 大逃杀' : 'AI 狼人杀'}</span><small>${game.maxPlayers || 8} 名玩家 · ${escapeHtml(updated)}</small></div><h3>${escapeHtml(game.name)}</h3><p>${escapeHtml(phaseText)}，数据来自真实后端状态与事件记录。</p><div class="seat-row"><b>${game.publicGame ? '公开观战' : '私有对局'}</b><em>${game.status === 'FINISHED' ? '查看复盘 →' : '进入观战 →'}</em></div></div>
    </article>`;
  }).join('');
  const openByIndex = (index) => openLobbyGame(games[Number(index)]);
  grid.querySelectorAll('[data-lobby-index]').forEach((card) => {
    card.addEventListener('click', () => openByIndex(card.dataset.lobbyIndex));
    card.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); openByIndex(card.dataset.lobbyIndex); }
    });
  });
  if (continueButton) {
    continueButton.disabled = false;
    continueButton.onclick = () => openLobbyGame(games[0]);
    continueButton.innerHTML = games[0].status === 'FINISHED' ? '查看最近复盘 <span>↗</span>' : '继续最近对局 <span>↗</span>';
  }
  const core = document.querySelector('.arena-core .core-label');
  if (core) core.innerHTML = `<span>${games[0].status === 'RUNNING' ? 'LIVE' : 'RECENT'}</span><strong>${escapeHtml(games[0].name)}</strong><small>${escapeHtml(gameStatusLabel(games[0].status))} · 第 ${games[0].currentRound || 1} 轮</small>`;
}

async function loadLobbyGames() {
  const grid = document.getElementById('live-game-grid');
  try {
    const visitor = await ensureVisitorSession();
    const games = await apiRequest('/api/games');
    renderLobbyGames(games || []);
  } catch (error) {
    if (grid) grid.innerHTML = `<div class="lobby-empty error"><span>!</span><h3>对局列表加载失败</h3><p>${escapeHtml(error.message)}</p><button class="secondary" type="button" data-retry-lobby>重新加载</button></div>`;
    grid?.querySelector('[data-retry-lobby]')?.addEventListener('click', loadLobbyGames);
  }
}

function renderSavedModels(configs) {
  const container = document.getElementById('saved-model-list');
  if (!container) return;
  container.innerHTML = configs.length ? configs.map((config) => `
    <article class="saved-model-card">
      <div><b>${escapeHtml(config.name)}</b><span>${escapeHtml(config.provider)} · ${escapeHtml(config.modelName)}</span></div>
      <div class="model-card-actions"><em>${config.enabled ? '已启用' : '已停用'}</em><button class="secondary test-model-button" data-config-id="${config.id}" type="button">测试连接</button><button class="secondary delete-model-button" data-config-id="${config.id}" data-config-name="${escapeHtml(config.name)}" type="button">删除</button></div>
    </article>`).join('') : '<p>还没有模型配置，请在左侧新增。</p>';
}

async function loadSavedModels() {
  try {
    const visitor = await ensureVisitorSession();
    renderSavedModels(await apiRequest('/api/model-configs'));
  } catch (error) {
    showToast(`加载配置失败：${error.message}`);
  }
}

document.getElementById('saved-model-list')?.addEventListener('click', async (event) => {
  const deleteButton = event.target.closest('.delete-model-button');
  if (deleteButton) {
    const configName = deleteButton.dataset.configName || '该模型配置';
    if (!window.confirm(`确定删除“${configName}”吗？此操作无法恢复。`)) return;
    deleteButton.disabled = true;
    try {
      const visitor = await ensureVisitorSession();
      await apiRequest(`/api/model-configs/${deleteButton.dataset.configId}`, { method: 'DELETE' });
      showToast('模型配置已删除');
      await loadSavedModels();
    } catch (error) {
      showToast(`删除失败：${error.message}`);
      deleteButton.disabled = false;
    }
    return;
  }
  const button = event.target.closest('.test-model-button');
  if (!button) return;
  const originalText = button.textContent;
  button.disabled = true;
  button.textContent = '测试中…';
  try {
    const visitor = await ensureVisitorSession();
    const result = await apiRequest(`/api/model-configs/${button.dataset.configId}/test`, { method: 'POST' });
    button.textContent = `成功 ${result.latencyMs}ms`;
    showToast(`连接成功：${result.response || 'OK'}`);
  } catch (error) {
    button.textContent = '连接失败';
    showToast(`测试失败：${error.message}`);
  } finally {
    setTimeout(() => { button.disabled = false; button.textContent = originalText; }, 3000);
  }
});

document.querySelectorAll('[data-page="models"]').forEach((button) => {
  button.addEventListener('click', loadSavedModels);
});

const standaloneModelForm = document.getElementById('standalone-model-form');
if (standaloneModelForm) standaloneModelForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  const submitButton = standaloneModelForm.querySelector('button[type="submit"]');
  submitButton.disabled = true;
  submitButton.textContent = '正在保存…';
  try {
    const visitor = await ensureVisitorSession();
    await apiRequest('/api/model-configs', {
      method: 'POST',
      body: JSON.stringify({
        name: document.getElementById('standalone-config-name').value.trim(),
        provider: document.getElementById('standalone-provider').value,
        modelName: document.getElementById('standalone-model-name').value.trim(),
        baseUrl: document.getElementById('standalone-base-url').value.trim() || null,
        apiKey: document.getElementById('standalone-api-key').value.trim() || null,
        modelOptions: { temperature: Number(document.getElementById('standalone-temperature').value || 0.7) }
      })
    });
    document.getElementById('standalone-api-key').value = '';
    showToast('模型配置已加密保存到 MySQL');
    await loadSavedModels();
  } catch (error) {
    showToast(`保存失败：${error.message}`);
  } finally {
    submitButton.disabled = false;
    submitButton.textContent = '保存模型配置';
  }
});

function bootstrapPage() {
  if (/^#public-game=\d+$/.test(window.location.hash)) {
    goTo('watch');
  } else if (['#create', '#models', '#replays'].includes(window.location.hash)) {
    goTo(window.location.hash.slice(1));
  } else if (window.location.hash === '#login' || window.location.hash === '#register') {
    loadLobbyGames();
    setTimeout(() => openAuthModal(window.location.hash.slice(1)), 0);
  } else {
    loadLobbyGames();
  }
}

window.addEventListener('hashchange', bootstrapPage);
setTimeout(bootstrapPage, 0);
