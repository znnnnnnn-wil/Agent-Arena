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

function mountArena3D(root, mode, payload = {}) {
  const host = root?.querySelector(`[data-arena-3d="${mode}"]`);
  if (!host) return;
  if (typeof payload.onSelectPlayer === 'function') {
    host.__arenaOnSelectPlayer = payload.onSelectPlayer;
    if (!host.__arenaSelectBound) {
      host.addEventListener('arena3d:select-player', (event) => host.__arenaOnSelectPlayer?.(event.detail?.playerId));
      host.__arenaSelectBound = true;
    }
  }
  const execute = () => {
    if (!host.isConnected || !window.AgentArena3D) return;
    window.AgentArena3D.mount(host, mode, payload);
  };
  if (window.AgentArena3D) execute();
  else window.addEventListener('agent-arena-3d-ready', execute, { once: true });
}

function detachArena3DHost(root, mode) {
  const host = root?.querySelector(`[data-arena-3d="${mode}"]`);
  host?.remove();
  return host || null;
}

function restoreArena3DHost(root, mode, retainedHost) {
  if (!retainedHost) return;
  root?.querySelector(`[data-arena-3d="${mode}"]`)?.replaceWith(retainedHost);
}

function goTo(page) {
  if (['home', 'create', 'models'].includes(page) && /^#(?:public-game=\d+|clip=\d+:[a-z0-9-]+)$/.test(window.location.hash)) {
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
  if (page === 'create') setTimeout(loadLineupTemplates, 0);
  if (page === 'models') setTimeout(() => { loadSavedModels(); loadAgentProfiles(); }, 0);
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
    abilities: [['语言推理', '公开发言会进入事件记录'], ['身份博弈', '身份信息按观战视角脱敏'], ['投票追踪', '每轮投票流向可回看']],
    phases: [['01', '狼人密谋', '秘密选择袭击目标'], ['02', '神职行动', '查验、守护与药剂决策'], ['03', '公开发言', '逐席陈述与交叉质疑'], ['04', '放逐投票', '多数决与阵营结算']],
    outcomes: [['好人阵营', '找出并放逐全部狼人'], ['狼人阵营', '让狼人数量追平其他存活者'], ['观战重点', '发言矛盾、投票流向、夜间行动反馈']]
  },
  BATTLE_ROYALE: {
    title: '一场受资源与空间挤压的生存决策实验', difficulty: '策略深度 · 中高',
    previewTitle: '8 人标准节点大逃杀', preview: '5 个特色区域 · 动态缩圈 · 装备搜寻 · 空投争夺',
    seats: ['1', '2', '3', '4', '5', '6', '7', '8'],
    abilities: [['路线决策', '只能沿区域连接移动和追击'], ['稀缺资源', '物资点会因反复搜索逐渐枯竭'], ['战斗压力', '避战者暴露，后期攻击伤害提高']],
    phases: [['01', '路线选择', '沿相邻节点移动到资源区'], ['02', '有限搜刮', '争抢会枯竭的武器与补给'], ['03', '空投争夺', '占领并守住空投行动窗口'], ['04', '强制决战', '暴露避战者并提高后期伤害']],
    outcomes: [['唯一胜者', '最后一名存活的智能体获胜'], ['进攻得分', '击杀和累计伤害大幅提高结算分'], ['观战重点', '追击路线、空投攻防、暴露目标和交战选择']]
  },
  TURING_HUNT: {
    title: '一场真人混入 AI 群体的身份识别实验', difficulty: '策略深度 · 高',
    previewTitle: '6 人图灵潜伏局', preview: '1 名真人 · 5 名 AI · 匿名回答 · 质询辩护 · 秘密投票',
    seats: ['AI', 'AI', '你', 'AI', 'AI', 'AI'],
    abilities: [['风格伪装', '真人需要让自己的表达融入 AI 群体'], ['群体识别', '怀疑与投票在提交完成后统一公开'], ['交叉审讯', '争议席位会进入质询、回应与辩护阶段']],
    phases: [['01', '匿名回答', '六名玩家独立回答同一道开放题'], ['02', '怀疑揭示', '公开聚合怀疑结果与争议席位'], ['03', '质询辩护', '围绕争议席位完成交叉问答'], ['04', '秘密投票', '统一揭票、平票辩护与至多一次重投']],
    outcomes: [['真人获胜', '三轮后仍未被识破，或 AI 连续误淘汰两名 AI'], ['AI 获胜', '投票淘汰隐藏在人群中的真人'], ['观战重点', '回答风格、怀疑变化、质询回应与误导链路']]
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
  if (abilityList) abilityList.innerHTML = content.abilities.map(([label, copy]) => `<div class="ability-fact"><span><b>${escapeHtml(label)}</b><small>${escapeHtml(copy)}</small></span></div>`).join('');
  if (phaseList) phaseList.innerHTML = content.phases.map(([number, label, copy]) => `<li><b>${number}</b><span><strong>${escapeHtml(label)}</strong><small>${escapeHtml(copy)}</small></span></li>`).join('');
  if (outcomeList) outcomeList.innerHTML = content.outcomes.map(([label, copy]) => `<div><b>${escapeHtml(label)}</b><span>${escapeHtml(copy)}</span></div>`).join('');
}

modeCards.forEach((card) => card.addEventListener('click', () => {
  modeCards.forEach((item) => item.classList.remove('selected'));
  card.classList.add('selected');
  selectedMode = ['WEREWOLF', 'BATTLE_ROYALE', 'TURING_HUNT'].includes(card.dataset.mode) ? card.dataset.mode : 'WEREWOLF';
  configuredGameName = selectedMode === 'WEREWOLF' ? 'AI 狼人杀' : selectedMode === 'BATTLE_ROYALE' ? 'AI 大逃杀' : '图灵潜伏局';
  renderModeDetails();
}));

let createStep = 1;
let modelDraft = null;
let seatModelConfigIds = Array(8).fill(null);
let seatAgentProfileIds = Array(8).fill(null);
let seatDisplayNames = Array.from({ length: 8 }, (_, index) => `${index + 1}号玩家`);
let availableSeatModels = [];
let availableAgentProfiles = [];
let configuredPlayerCount = 8;
let configuredMaxRounds = 20;
let humanSeatNo = 3;
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
    : selectedMode === 'TURING_HUNT'
      ? `<div class="rules-workbench turing-rules"><header><div><span class="kicker">HUMAN IN THE LOOP</span><h3>1 名真人潜入 5 名 AI</h3><p>真人席位可调整；模型身份、提交内容和逐票流向会按照阶段严格隐藏。</p></div><span class="rule-lock">固定 6 席 · 3 轮</span></header><div class="turing-human-seat"><label for="human-seat">你的匿名席位</label><select id="human-seat" aria-label="真人席位">${Array.from({ length: 6 }, (_, index) => `<option value="${index + 1}" ${humanSeatNo === index + 1 ? 'selected' : ''}>${index + 1} 号席位</option>`).join('')}</select><small>开局前只有房主知道；结束后统一公开所有身份与模型。</small></div><div class="rule-phase-strip"><span>匿名回答</span><i>→</i><span>怀疑揭示</span><i>→</i><span>质询辩护</span><i>→</i><span>秘密投票</span></div><div class="rule-chips"><span>所有提交完成后统一揭示</span><span>平票先辩护再重投一次</span><span>真人被淘汰则 AI 获胜</span><span>误淘汰 2 名 AI 则真人获胜</span><span>真人坚持 3 轮则获胜</span></div></div>`
      : `<div class="rules-workbench battle-rules"><header><div><span class="kicker">SURVIVAL PRESETS</span><h3>选择生存节奏</h3><p>预设会真实调整参与人数与最大回合，仍可在下方精细修改。</p></div><span class="rule-lock">6–10 席</span></header><div class="battle-presets"><button type="button" data-players="6" data-rounds="12"><b>闪电局</b><span>6 人 · 12 回合</span><small>交战更早，适合快速观察</small></button><button type="button" class="active" data-players="8" data-rounds="20"><b>标准局</b><span>8 人 · 20 回合</span><small>资源、移动和战斗更均衡</small></button><button type="button" data-players="10" data-rounds="30"><b>长线局</b><span>10 人 · 30 回合</span><small>联盟与区域博弈更复杂</small></button></div><div class="battle-tuning"><label>玩家人数 <span><input id="player-count" type="range" value="${configuredPlayerCount}" min="6" max="10"><output id="player-count-output">${configuredPlayerCount}</output></span></label><label>最大回合 <span><input id="max-rounds" type="range" value="${configuredMaxRounds}" min="12" max="30"><output id="max-rounds-output">${configuredMaxRounds}</output></span></label></div><div class="region-strip"><span>中央废墟<small>武器 / 弹药</small></span><span>北部密林<small>食物 / 医疗</small></span><span>东岸码头<small>饮水 / 弹药</small></span><span>南部工事<small>护甲 / 弹药</small></span><span>西侧营地<small>医疗 / 补给</small></span></div></div>`;
  const confirmationCopy = selectedMode === 'WEREWOLF'
    ? '固定 8 席 · 进阶角色 · 阵营胜负'
    : selectedMode === 'TURING_HUNT' ? `你在 ${humanSeatNo} 号位 · 5 名 AI · 最多 3 轮` : `${configuredPlayerCount} 名生存者 · 最多 ${configuredMaxRounds} 回合 · 唯一胜者`;
  const configuredModelSeats = selectedMode === 'TURING_HUNT'
    ? seatModelConfigIds.filter((value, index) => index + 1 !== humanSeatNo && Boolean(value)).length
    : seatModelConfigIds.filter(Boolean).length;
  const confirmPanel = `<div class="confirm-workbench"><div class="confirm-summary"><span class="kicker">MATCH SUMMARY</span><h3>${escapeHtml(MODE_CONTENT[selectedMode].previewTitle)}</h3><p>${escapeHtml(confirmationCopy)}</p><div><span><b>${configuredModelSeats}</b> 个模型席位</span><span><b>${seatAgentProfileIds.filter(Boolean).length}</b> 个人格席位</span><span><b>结构化</b> 精彩片段</span></div></div><div class="confirm-fields"><label>对局名称<input id="game-name" maxlength="128" value="${escapeHtml(configuredGameName)}" required></label><label class="visibility-option"><input id="public-game" type="checkbox" ${configuredPublicGame ? 'checked' : ''}><span><b>公开观战</b><small>其他人只会看到当前阶段已公开的内容；秘密提交、真人身份与模型配置不会提前泄露。</small></span></label></div>${renderSeatAssignmentReview()}</div>`;
  const panels = {
    2: rulePanel,
    3: `<div class="model-config-panel seat-assignment"><div class="seat-assignment-head"><div><span class="kicker">AGENT SEATS</span><h3>组合模型与人格</h3><p>${selectedMode === 'TURING_HUNT' ? '你的席位由浏览器直接操作；其余五席可分别选择模型，六个席位都可选择公开人格，身份在终局前保持隐藏。' : '模型决定推理能力，人格决定表达风格和合法策略偏好；狼人杀会默认分配八种不同人格。'}</p></div><button class="secondary" type="button" id="open-model-center">＋ 管理模型与人格</button></div><div id="seat-config-list" class="seat-config-list"><p>正在加载模型与人格配置…</p></div><small class="model-hint">${selectedMode === 'TURING_HUNT' ? '真人席位不会绑定或调用模型；未选择模型的 AI 席位使用确定性规则策略，推理与表达体验可能弱于已配置模型。' : '没有选择模型的席位使用规则 AI；清空人格时，旧对局兼容策略仍会按座位使用默认人格。'}</small></div>`,
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
  const templateShelf = document.getElementById('lineup-template-shelf');
  if (templateShelf) templateShelf.style.display = createStep === 1 ? '' : 'none';
  wireCreateStepControls();
}

function renderSeatAssignmentReview() {
  const count = selectedMode === 'WEREWOLF' ? 8 : selectedMode === 'TURING_HUNT' ? 6 : configuredPlayerCount;
  return `<div class="confirm-lineup"><h4>席位配置</h4>${Array.from({ length: count }, (_, index) => {
    const model = availableSeatModels.find((item) => String(item.id) === String(seatModelConfigIds[index]));
    const persona = availableAgentProfiles.find((item) => String(item.id) === String(seatAgentProfileIds[index]));
    const human = selectedMode === 'TURING_HUNT' && index + 1 === humanSeatNo;
    return `<article class="${selectedMode === 'TURING_HUNT' ? 'turing-seat-review' : ''} ${human ? 'human-seat-review' : ''}"><b>${index + 1}号 · ${escapeHtml(seatDisplayNames[index] || `${index + 1}号玩家`)}${human ? ' · 你' : ''}</b>${selectedMode === 'TURING_HUNT' ? `<span>类型：${human ? '真人' : 'AI'}</span>` : ''}<span>模型：${human ? '真人直接操作' : escapeHtml(model ? `${model.name} · ${model.modelName}` : '规则 AI')}</span><span>人格：${escapeHtml(persona?.name || (human ? '由你现场表达' : '座位默认人格'))}</span></article>`;
  }).join('')}</div>`;
}

function wireCreateStepControls() {
  if (createStep !== 2) return;
  if (selectedMode === 'TURING_HUNT') {
    document.getElementById('human-seat')?.addEventListener('change', (event) => {
      humanSeatNo = Number(event.target.value || 3);
      seatModelConfigIds[humanSeatNo - 1] = null;
    });
    return;
  }
  if (selectedMode !== 'BATTLE_ROYALE') return;
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
  if (selectedMode === 'TURING_HUNT') {
    humanSeatNo = Math.max(1, Math.min(6, Number(document.getElementById('human-seat')?.value || humanSeatNo || 3)));
    configuredPlayerCount = 6;
    configuredMaxRounds = 3;
    seatModelConfigIds = Array.from({ length: 6 }, (_, index) => index + 1 === humanSeatNo ? null : seatModelConfigIds[index] || null);
    seatAgentProfileIds = Array.from({ length: 6 }, (_, index) => seatAgentProfileIds[index] || null);
    seatDisplayNames = Array.from({ length: 6 }, (_, index) => seatDisplayNames[index] || (index + 1 === humanSeatNo ? '匿名玩家' : `${index + 1}号玩家`));
    return;
  }
  if (selectedMode !== 'BATTLE_ROYALE') return;
  configuredPlayerCount = Math.max(6, Math.min(10, Number(document.getElementById('player-count')?.value || 8)));
  configuredMaxRounds = Math.max(12, Math.min(30, Number(document.getElementById('max-rounds')?.value || 20)));
  if (seatModelConfigIds.length !== configuredPlayerCount) {
    seatModelConfigIds = Array.from({ length: configuredPlayerCount }, (_, index) => seatModelConfigIds[index] || null);
  }
  if (seatAgentProfileIds.length !== configuredPlayerCount) {
    seatAgentProfileIds = Array.from({ length: configuredPlayerCount }, (_, index) => seatAgentProfileIds[index] || null);
  }
  if (seatDisplayNames.length !== configuredPlayerCount) {
    seatDisplayNames = Array.from({ length: configuredPlayerCount }, (_, index) => seatDisplayNames[index] || `${index + 1}号幸存者`);
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
  seatAgentProfileIds = Array.from(document.querySelectorAll('.seat-persona-select'))
    .map((select) => select.value ? Number(select.value) : null);
  seatDisplayNames = Array.from(document.querySelectorAll('.seat-name-input')).map((input, index) => input.value.trim() || `${index + 1}号${selectedMode === 'BATTLE_ROYALE' ? '幸存者' : '玩家'}`);
  if (selectedMode === 'TURING_HUNT') seatModelConfigIds[humanSeatNo - 1] = null;
}

async function loadSeatModelOptions() {
  const container = document.getElementById('seat-config-list');
  if (!container) return;
  try {
    const visitor = await ensureVisitorSession();
    const [configs, profiles] = await Promise.all([
      apiRequest('/api/model-configs'),
      apiRequest('/api/agent-profiles')
    ]);
    availableSeatModels = configs;
    availableAgentProfiles = profiles;
    if (['WEREWOLF', 'TURING_HUNT'].includes(selectedMode) && !seatAgentProfileIds.some(Boolean)) {
      const presetOrder = ['AGGRESSIVE_LEADER', 'CAUTIOUS_ANALYST', 'LOGIC_DETECTIVE', 'EMOTIONAL_COUNTER',
        'DISGUISE_EXPERT', 'MAJORITY_FOLLOWER', 'HIGH_RISK_GAMBLER', 'LONG_MEMORY'];
      const builtIns = [...profiles].filter((profile) => profile.builtIn)
        .sort((left, right) => presetOrder.indexOf(left.code) - presetOrder.indexOf(right.code));
      const presetCount = selectedMode === 'TURING_HUNT' ? 6 : 8;
      if (builtIns.length >= presetCount) seatAgentProfileIds = builtIns.slice(0, presetCount).map((profile) => Number(profile.id));
    }
    const roleNames = selectedMode === 'WEREWOLF'
      ? Array.from({ length: 8 }, (_, index) => seatDisplayNames[index] || `${index + 1}号玩家`)
      : selectedMode === 'TURING_HUNT'
        ? Array.from({ length: 6 }, (_, index) => seatDisplayNames[index] || `${index + 1}号玩家`)
        : Array.from({ length: configuredPlayerCount }, (_, index) => seatDisplayNames[index] || `${index + 1}号幸存者`);
    const options = configs.map((config) => `<option value="${Number(config.id)}">${escapeHtml(config.name)} · ${escapeHtml(config.provider)}/${escapeHtml(config.modelName)}</option>`).join('');
    const personaOptions = profiles.map((profile) => `<option value="${Number(profile.id)}">${escapeHtml(profile.avatarEmoji || 'AI')} ${escapeHtml(profile.name)} · ${escapeHtml((profile.traits || []).slice(0, 2).join('/'))}</option>`).join('');
    container.innerHTML = roleNames.map((role, index) => {
      const human = selectedMode === 'TURING_HUNT' && index + 1 === humanSeatNo;
      return `<label class="seat-config-row ${human ? 'human-seat-row' : ''}"><span><b>${index + 1}</b>席位 ${index + 1}<em>${human ? '你 · 真人' : selectedMode === 'TURING_HUNT' ? 'AI' : ''}</em></span><input class="seat-name-input" maxlength="64" value="${escapeHtml(role)}" aria-label="席位 ${index + 1} 名称"><select class="seat-model-select" aria-label="席位 ${index + 1} 模型" ${human ? 'disabled' : ''}><option value="">${human ? '真人直接操作（不调用模型）' : '规则 AI（不调用模型）'}</option>${human ? '' : options}</select><select class="seat-persona-select" aria-label="席位 ${index + 1} 人格"><option value="">${human ? '选择你的公开人格' : '座位默认人格'}</option>${personaOptions}</select></label>`;
    }).join('');
    container.querySelectorAll('.seat-model-select').forEach((select, index) => {
      if (seatModelConfigIds[index]) select.value = String(seatModelConfigIds[index]);
    });
    container.querySelectorAll('.seat-persona-select').forEach((select, index) => {
      if (seatAgentProfileIds[index]) select.value = String(seatAgentProfileIds[index]);
    });
    if (selectedMode === 'TURING_HUNT') {
      seatModelConfigIds[humanSeatNo - 1] = null;
    }
    document.getElementById('open-model-center')?.addEventListener('click', () => {
      captureModelDraft();
      goTo('models');
      loadSavedModels();
    });
  } catch (error) {
    container.innerHTML = `<p>模型配置加载失败：${escapeHtml(error.message)}</p>`;
  }
}

function riskPreferenceLabel(value) {
  return ({ CAUTIOUS: '谨慎', BALANCED: '平衡', BOLD: '激进' })[value] || '平衡';
}

function renderLineupTemplates(templates) {
  const container = document.getElementById('lineup-template-list');
  if (!container) return;
  container.innerHTML = templates.length ? templates.map((template) => `<article class="lineup-template-card">
    <div><span>${template.mode === 'WEREWOLF' ? '◒' : template.mode === 'TURING_HUNT' ? '⌁' : '⌖'}</span><b>${escapeHtml(template.name)}</b><small>${template.playerCount} 席 · ${template.modelSeatCount} 个模型 · ${template.personaSeatCount} 个人格</small></div>
    <footer><button class="primary small" type="button" data-use-template="${Number(template.id)}" data-template-mode="${escapeHtml(template.mode)}">使用模板</button><button class="text-button" type="button" data-delete-template="${Number(template.id)}">删除</button></footer>
  </article>`).join('') : '<p class="template-empty">尚未保存阵容模板。完成一场配置后，可在观战页将它保存为模板。</p>';
}

async function loadLineupTemplates() {
  const container = document.getElementById('lineup-template-list');
  if (!container) return;
  try {
    await ensureVisitorSession();
    renderLineupTemplates(await apiRequest('/api/lineup-templates'));
  } catch (error) {
    container.innerHTML = `<p class="template-empty">模板加载失败：${escapeHtml(error.message)}</p>`;
  }
}

async function useLineupTemplate(templateId, mode, button) {
  button.disabled = true;
  button.textContent = '正在创建…';
  try {
    const game = await apiRequest(`/api/lineup-templates/${templateId}/games`, {
      method: 'POST', body: JSON.stringify({})
    });
    localStorage.setItem('agentArenaSession', JSON.stringify({ gameId: game.id, mode: game.mode || mode }));
    const effectiveMode = game.mode || mode;
    const startPath = effectiveMode === 'WEREWOLF' ? 'start' : effectiveMode === 'TURING_HUNT' ? 'turing-start' : 'battle-start';
    await apiRequest(`/api/games/${game.id}/${startPath}`, { method: 'POST' });
    showToast(`已使用阵容模板创建对局 #${game.id}`);
    goTo('watch');
  } catch (error) {
    showToast(`模板创建失败：${error.message}`);
    button.disabled = false;
    button.textContent = '使用模板';
  }
}

document.getElementById('lineup-template-list')?.addEventListener('click', async (event) => {
  const useButton = event.target.closest('[data-use-template]');
  if (useButton) {
    await useLineupTemplate(useButton.dataset.useTemplate, useButton.dataset.templateMode, useButton);
    return;
  }
  const deleteButton = event.target.closest('[data-delete-template]');
  if (!deleteButton || !window.confirm('确定删除这个阵容模板吗？')) return;
  try {
    await apiRequest(`/api/lineup-templates/${deleteButton.dataset.deleteTemplate}`, { method: 'DELETE' });
    showToast('阵容模板已删除');
    await loadLineupTemplates();
  } catch (error) {
    showToast(`删除失败：${error.message}`);
  }
});

async function apiRequest(url, options = {}) {
  const controller = new AbortController();
  const { headers = {}, signal, timeoutMs = 35000, ...fetchOptions } = options;
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
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
    if (error.name === 'AbortError') {
      const timeoutError = new Error(`请求超过 ${Math.ceil(timeoutMs / 1000)} 秒，请检查后端或模型服务`);
      timeoutError.code = 'REQUEST_TIMEOUT';
      timeoutError.status = 0;
      throw timeoutError;
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

async function createRealGame() {
  nextButton.disabled = true;
  nextButton.textContent = '正在创建…';
  let createdGame = null;
  try {
    const visitor = await ensureVisitorSession();
    const selectedIds = seatModelConfigIds.filter((id) => id != null);
    const modelConfigIds = selectedIds.length ? seatModelConfigIds : [];
    const selectedProfiles = seatAgentProfileIds.filter((id) => id != null);
    const agentProfileIds = selectedProfiles.length ? seatAgentProfileIds : [];
    captureConfirmation();
    if (!configuredGameName) throw new Error('请填写对局名称');
    const common = { name: configuredGameName, publicGame: configuredPublicGame, modelConfigIds, agentProfileIds, playerDisplayNames: seatDisplayNames };
    let game;
    if (selectedMode === 'WEREWOLF') {
      game = await apiRequest('/api/games/werewolf', { method: 'POST', body: JSON.stringify(common) });
    } else if (selectedMode === 'TURING_HUNT') {
      const toSeatMap = (values, include) => Object.fromEntries(values
        .map((value, index) => [index + 1, value])
        .filter(([seatNo, value]) => include(seatNo, value)));
      game = await apiRequest('/api/games/turing-hunt', {
        method: 'POST',
        body: JSON.stringify({
          name: configuredGameName,
          publicGame: configuredPublicGame,
          humanSeatNo,
          playerDisplayNames: toSeatMap(seatDisplayNames.slice(0, 6), (seatNo, value) => Boolean(value)),
          seatModelConfigIds: toSeatMap(seatModelConfigIds.slice(0, 6), (seatNo, value) => seatNo !== humanSeatNo && value != null),
          seatAgentProfileIds: toSeatMap(seatAgentProfileIds.slice(0, 6), (seatNo, value) => value != null)
        })
      });
    } else {
      game = await apiRequest('/api/games/battle-royale', { method: 'POST', body: JSON.stringify({ ...common, playerCount: configuredPlayerCount, maxRounds: configuredMaxRounds }) });
    }
    createdGame = game;
    localStorage.setItem('agentArenaSession', JSON.stringify({ gameId: game.id, mode: selectedMode }));
    const startPath = selectedMode === 'WEREWOLF' ? 'start' : selectedMode === 'TURING_HUNT' ? 'turing-start' : 'battle-start';
    await apiRequest(`/api/games/${game.id}/${startPath}`, { method: 'POST' });
    showToast(`真实对局 #${game.id} 已写入 MySQL`);
    setTimeout(() => goTo('watch'), 450);
  } catch (error) {
    if (createdGame) {
      showToast(`对局已创建但未启动：${error.message}`);
      setTimeout(() => goTo('watch'), 650);
    } else {
      showToast(`创建失败：${error.message}`);
    }
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

async function shareHighlightClip(gameId, clipId) {
  const url = `${window.location.origin}${window.location.pathname}#clip=${Number(gameId)}:${clipId}`;
  try {
    await navigator.clipboard.writeText(url);
    showToast('精彩片段链接已复制');
  } catch {
    showToast(`请复制片段链接：${url}`);
  }
}

async function saveCurrentLineupTemplate() {
  const session = getGameSession();
  if (!session?.gameId || session.publicView) return;
  const name = window.prompt('为这套模型与人格阵容命名', `${lastRealGame?.name || '我的阵容'} · 模板`);
  if (!name?.trim()) return;
  try {
    await apiRequest(`/api/lineup-templates/from-game/${session.gameId}`, {
      method: 'POST', body: JSON.stringify({ name: name.trim() })
    });
    showToast('阵容模板已保存，可在创建对局时一键复用');
  } catch (error) {
    showToast(`保存模板失败：${error.message}`);
  }
}

async function rematchCurrentGame(button) {
  const session = getGameSession();
  if (!session?.gameId || session.publicView) return;
  button.disabled = true;
  button.textContent = '正在创建重赛…';
  try {
    const game = await apiRequest(`/api/games/${session.gameId}/rematch`, {
      method: 'POST', body: JSON.stringify({})
    });
    localStorage.setItem('agentArenaSession', JSON.stringify({ gameId: game.id, mode: game.mode }));
    const startPath = game.mode === 'WEREWOLF' ? 'start' : game.mode === 'TURING_HUNT' ? 'turing-start' : 'battle-start';
    await apiRequest(`/api/games/${game.id}/${startPath}`, { method: 'POST' });
    currentRealEvents = [];
    currentMatchProjection = { schemaVersion: 1, commentary: [], highlights: [] };
    showToast(`重赛 #${game.id} 已开始`);
    await loadCurrentGame(false);
  } catch (error) {
    showToast(`创建重赛失败：${error.message}`);
    button.disabled = false;
    button.textContent = '同配置重赛';
  }
}

let currentRealEvents = [];
let currentMatchProjection = { schemaVersion: 1, commentary: [], highlights: [] };
let liveEventSource = null;
let liveStreamGameId = null;
let liveStreamStatus = '未连接实时事件';
let lastRealGame = null;
let lastRealPlayers = [];
let selectedReportTab = 'commentary';
let selectedMobileArenaTab = 'arena';
let selectedTimelineSequence = null;
let watchActionError = '';
let turingMobileTab = 'game';
let turingHistoryOpen = false;
let turnStartedAt = null;

function modelDisplayLabel(player = {}) {
  if (player.localStrategy) return '规则 AI';
  return player.modelDisplayName || [player.modelProvider, player.modelName].filter(Boolean).join(' · ') || '模型未标注';
}

function currentActorModelLabel(game = {}) {
  if (game.currentActorLocalStrategy) return '规则 AI';
  return game.currentActorModelDisplayName
    || [game.currentActorModelProvider, game.currentActorModelName].filter(Boolean).join(' · ')
    || (game.currentActorName ? '模型未标注' : '等待阶段切换');
}

function ensureWatchDashboard() {
  const watchPage = document.getElementById('watch-page');
  if (!watchPage) return null;
  const shell = watchPage.querySelector('.watch-shell');
  if (shell) shell.style.display = 'none';
  let dashboard = watchPage.querySelector('.real-watch-dashboard');
  if (!dashboard) {
    dashboard = document.createElement('div');
    dashboard.className = 'real-watch-dashboard';
    watchPage.appendChild(dashboard);
  }
  return dashboard;
}

function renderWatchState(kind, title, copy, retry = false) {
  const dashboard = ensureWatchDashboard();
  if (!dashboard) return;
  if (kind === 'loading') {
    dashboard.innerHTML = `<div class="watch-skeleton" aria-label="正在加载对局"><header><i></i><span></span><em></em></header><section><div></div><div></div><div></div></section><p>${escapeHtml(copy)}</p></div>`;
  } else {
    dashboard.innerHTML = `<div class="watch-feedback ${escapeHtml(kind)}"><i></i><span>LOAD ERROR</span><h2>${escapeHtml(title)}</h2><p>${escapeHtml(copy)}</p>${retry ? '<button class="primary" type="button" data-watch-retry>重新加载</button>' : ''}</div>`;
  }
  dashboard.querySelector('[data-watch-retry]')?.addEventListener('click', () => loadCurrentGame());
}

function updateLiveStreamIndicator() {
  const indicator = document.querySelector('.stream-state');
  if (indicator) {
    indicator.textContent = liveStreamStatus;
    indicator.classList.toggle('connected', liveStreamStatus.includes('已连接'));
  }
}

function updateThinkingClock() {
  const elapsed = document.querySelector('.thinking-elapsed');
  if (!elapsed) return;
  if (!turnStartedAt || !isTurnInFlight()) {
    elapsed.textContent = '';
    elapsed.hidden = true;
    return;
  }
  elapsed.hidden = false;
  elapsed.textContent = `已等待 ${Math.max(1, Math.floor((Date.now() - turnStartedAt) / 1000))} 秒`;
}

window.setInterval(updateThinkingClock, 1000);

function getGameSession() {
  const publicMatch = window.location.hash.match(/^#public-game=(\d+)$/);
  if (publicMatch) return { gameId: Number(publicMatch[1]), publicView: true };
  const clipMatch = window.location.hash.match(/^#clip=(\d+):([a-z0-9-]+)$/);
  if (clipMatch) return { gameId: Number(clipMatch[1]), publicView: true, clipId: clipMatch[2] };
  try { return JSON.parse(localStorage.getItem('agentArenaSession') || 'null'); } catch { return null; }
}

function gameApiBase(session) {
  return session.publicView ? '/api/public/games' : '/api/games';
}

function connectLiveStream(game, session) {
  if (!game.publicGame) {
    liveStreamStatus = session.publicView ? '该对局未开放实时流' : '私有对局 · 操作后刷新';
    updateLiveStreamIndicator();
    return;
  }
  if (liveStreamGameId === game.id) return;
  if (liveEventSource) liveEventSource.close();
  liveStreamGameId = game.id;
  liveStreamStatus = '实时事件连接中';
  liveEventSource = new EventSource(`/api/public/games/${game.id}/stream`);
  liveEventSource.onopen = () => {
    liveStreamStatus = '实时事件已连接';
    updateLiveStreamIndicator();
    // A reconnect can miss the final event, so always reconcile persisted state.
    loadCurrentGame(true);
  };
  liveEventSource.addEventListener('game-event', () => loadCurrentGame(true));
  liveEventSource.onerror = () => { liveStreamStatus = '实时事件重连中'; updateLiveStreamIndicator(); };
}

async function loadAllReplayEvents(base, gameId, afterSequence = 0) {
  const events = [];
  let cursor = afterSequence;
  while (true) {
    const page = await apiRequest(`${base}/${gameId}/replay?afterSequence=${cursor}&limit=500`);
    if (!Array.isArray(page) || !page.length) return events;
    const nextCursor = Number(page[page.length - 1].sequenceNo);
    if (!Number.isSafeInteger(nextCursor) || nextCursor <= cursor) throw new Error('回放事件序号异常');
    events.push(...page);
    if (page.length < 500) return events;
    cursor = nextCursor;
  }
}

let watchLoadPromise = null;
let watchRefreshPending = false;
let watchFullRefreshPending = false;

function loadCurrentGame(incremental = false) {
  watchRefreshPending = true;
  watchFullRefreshPending ||= !incremental;
  if (!watchLoadPromise) {
    watchLoadPromise = (async () => {
      let result;
      while (watchRefreshPending) {
        const full = watchFullRefreshPending;
        watchRefreshPending = false;
        watchFullRefreshPending = false;
        result = await fetchCurrentGame(!full);
      }
      return result;
    })().finally(() => { watchLoadPromise = null; });
  }
  return watchLoadPromise;
}

async function fetchCurrentGame(incremental = false) {
  const session = getGameSession();
  if (!session) {
    showToast('请先创建一场对局');
    return;
  }
  if (!incremental) renderWatchState('loading', '正在进入竞技场', '同步对局、玩家状态与事件记录…');
  try {
    const base = gameApiBase(session);
    const afterSequence = incremental && currentRealEvents.length
      ? Math.max(...currentRealEvents.map((event) => Number(event.sequenceNo) || 0)) : 0;
    const [game, players, events, projection] = await Promise.all([
      apiRequest(`${base}/${session.gameId}`),
      apiRequest(`${base}/${session.gameId}/players`),
      loadAllReplayEvents(base, session.gameId, afterSequence),
      apiRequest(`${base}/${session.gameId}/projection`).catch(() => null)
    ]);
    const activeSession = getGameSession();
    if (activeSession?.gameId !== session.gameId || activeSession?.publicView !== session.publicView
        || !document.getElementById('watch-page')?.classList.contains('active')) return game;
    const previousState = JSON.stringify([lastRealGame, lastRealPlayers, currentRealEvents, currentMatchProjection]);
    currentRealEvents = incremental
      ? [...new Map([...currentRealEvents, ...events].map((event) => [Number(event.sequenceNo), event])).values()]
          .sort((left, right) => Number(left.sequenceNo) - Number(right.sequenceNo))
      : events;
    window.currentRealPlayers = players;
    lastRealGame = game;
    lastRealPlayers = players;
    currentMatchProjection = projection || { schemaVersion: 1, commentary: [], highlights: [] };
    // Reconnects often return the same state. Preserve form input and the mounted stage in that case.
    const stateChanged = previousState !== JSON.stringify([game, players, currentRealEvents, currentMatchProjection]);
    if (!incremental || stateChanged) renderRealWatch(game, players, currentRealEvents, session.publicView === true);
    if (session.clipId && (!incremental || stateChanged)) setTimeout(() => document.querySelector(`[data-highlight-id="${CSS.escape(session.clipId)}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 80);
    connectLiveStream(game, session);
    return game;
  } catch (error) {
    showToast(`加载真实对局失败：${error.message}`);
    if (!incremental) renderWatchState('error', '对局加载失败', error.message, true);
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
    const [game, players, events, summary, projection] = await Promise.all([
      apiRequest(`${base}/${session.gameId}`),
      apiRequest(`${base}/${session.gameId}/players`),
      loadAllReplayEvents(base, session.gameId),
      apiRequest(`${base}/${session.gameId}/summary`),
      apiRequest(`${base}/${session.gameId}/projection`).catch(() => null)
    ]);
    const groups = groupGameEvents(events).slice(-18).reverse();
    const winnerText = summary.winner && summary.winner !== 'null' ? summary.winner : game.status === 'FINISHED' ? '已结算' : '对局进行中';
    const playerRows = [...players].sort((a, b) => game.mode === 'BATTLE_ROYALE'
      ? (b.kills || 0) - (a.kills || 0) || (b.totalDamage || 0) - (a.totalDamage || 0)
      : (a.status === 'ALIVE' ? -1 : 1) - (b.status === 'ALIVE' ? -1 : 1));
    const highlights = [...(projection?.highlights || [])].reverse();
    const turingReplay = game.mode === 'TURING_HUNT';
    const recap = game.turingRecap || {};
    if (turingReplay) {
      renderTuringReplayPage(container, game, players, events, summary, highlights);
      return;
    }
    const recapPanel = turingReplay && game.status === 'FINISHED' ? `<section class="replay-turing-recap"><header><span>潜伏实验结论</span><h3>${(game.winner || recap.winner) === 'HUMAN' ? '真人成功潜伏' : 'AI 成功识破真人'}</h3><p>${escapeHtml(turingFinishedReasonLabel(game.finishedReason || recap.finishedReason))}</p></header><div><span>误淘汰 AI <b>${Number(recap.wrongAiEliminationCount || 0)}</b></span><span>淘汰顺序 <b>${(recap.eliminationOrder || []).length ? recap.eliminationOrder.map((seat) => `${seat}号`).join(' → ') : '无人'}</b></span><span>最先识破 <b>${recap.firstCorrectSuspectSeatNo ? `${recap.firstCorrectSuspectSeatNo}号` : '无人'}</b></span><span>判断最准确 <b>${recap.mostAccurateAiSeatNo ? `${recap.mostAccurateAiSeatNo}号` : '未产生'}</b></span></div></section>` : '';
    const replayPlayerDetail = (player) => game.mode === 'BATTLE_ROYALE'
      ? `${escapeHtml(player.weaponName || '徒手')} · 伤害 ${player.totalDamage || 0}`
      : turingReplay ? (game.identitiesRevealed ? `${player.playerType === 'HUMAN' || player.roleCode === 'HUMAN' ? '真人' : 'AI'} · ${escapeHtml(player.playerType === 'HUMAN' || player.roleCode === 'HUMAN' ? '真人直接操作' : modelDisplayLabel(player))}` : '身份与模型尚未揭示')
        : escapeHtml(werewolfRoleLabel(player.roleCode));
    container.innerHTML = `<div class="replay-title"><div><span>${escapeHtml(gameModeLabel(game.mode))}</span><h2>${escapeHtml(game.name)} · #${game.id}</h2><p>第 ${game.currentRound || 1} 轮 · ${escapeHtml(gameStatusLabel(game.status))}</p></div><b>${escapeHtml(winnerText)}</b></div>
      <div class="replay-stats"><article><span>事件总数</span><b>${summary.eventCount || 0}</b></article><article><span>行动数量</span><b>${groupGameEvents(events).length}</b></article><article><span>淘汰人数</span><b>${summary.eliminationCount || 0}</b></article><article><span>当前轮次</span><b>${game.currentRound || 1}</b></article></div>
      ${recapPanel}
      <div class="replay-grid"><section><header><h3>服务端精彩片段</h3><span>${highlights.length} 条</span></header><div class="key-event-list highlight-clip-list">${highlights.length ? highlights.map((item, index) => `<article data-highlight-id="${escapeHtml(item.clipId)}"><i>${String(index + 1).padStart(2, '0')}</i><div><b>${escapeHtml(item.title)}</b><p>${escapeHtml(item.narration)}</p><small>第 ${Number(item.roundNo || 0)} 轮 · #${Number(item.startSequenceNo)}–${Number(item.endSequenceNo)}</small></div>${game.publicGame ? `<button class="text-button" data-share-clip="${escapeHtml(item.clipId)}" data-share-game="${Number(game.id)}" type="button">分享</button>` : ''}</article>`).join('') : '<p class="muted-copy">对局继续推进后会生成关键事件。</p>'}</div></section>
      <aside><header><h3>${game.mode === 'BATTLE_ROYALE' ? '生存排名' : turingReplay ? '身份与模型揭晓' : '角色结局'}</h3><span>${players.length} 名玩家</span></header><div class="replay-player-list">${playerRows.map((player, index) => `<article><i>${index + 1}</i><div><b>${escapeHtml(player.displayName)}</b><span>${replayPlayerDetail(player)}</span></div><em>${game.mode === 'BATTLE_ROYALE' ? `☠ ${player.kills || 0}` : player.status === 'ALIVE' ? '存活' : '出局'}</em></article>`).join('')}</div></aside></div>
      <section class="replay-timeline"><header><h3>最近事件</h3><span>按时间倒序</span></header>${groups.map((group) => { const event = group.primary || group.decision; const info = describeGameEvent(group, players); return `<article><i>${escapeHtml(info[0])}</i><div><b>${escapeHtml(info[1])}</b><p>${escapeHtml(info[2])}</p></div><span>第 ${event.roundNo} 轮 · #${event.sequenceNo}</span></article>`; }).join('') || '<p class="muted-copy">暂无事件。</p>'}</section>`;
    container.querySelectorAll('[data-share-clip]').forEach((button) => button.addEventListener('click', () => shareHighlightClip(button.dataset.shareGame, button.dataset.shareClip)));
  } catch (error) {
    container.innerHTML = `<div class="empty-state compact"><span>!</span><h2>复盘加载失败</h2><p>${escapeHtml(error.message)}</p></div>`;
  }
}

function renderTuringReplayPage(container, game, players, events, summary, highlights) {
  const recap = game.turingRecap || {};
  const rounds = recap.roundSummaries || [];
  const winner = game.winner || recap.winner;
  const humanSeat = recap.humanSeatNo || players.find((player) => player.playerType === 'HUMAN' || player.roleCode === 'HUMAN')?.seatNo;
  const identityRows = [...players].sort((a, b) => Number(a.seatNo) - Number(b.seatNo)).map((player) => {
    const human = player.playerType === 'HUMAN' || player.roleCode === 'HUMAN';
    const modelLabel = human ? '真人玩家' : player.localStrategy ? '系统策略' : modelDisplayLabel(player);
    return `<article><i>${Number(player.seatNo)}</i><div><b>${escapeHtml(player.displayName)}</b><span>${escapeHtml(modelLabel)}</span><small>${escapeHtml(player.agentProfileName || '匿名人格')}</small></div><em>${game.identitiesRevealed ? (human ? 'HUMAN' : 'AI') : 'HIDDEN'}</em></article>`;
  }).join('');
  const roundPanels = rounds.map((round, index) => {
    const answers = (round.answers || []).map((answer) => `<article><i>${Number(answer.seatNo)}</i><p>${escapeHtml(answer.answer || '')}</p></article>`).join('');
    const suspicions = (round.suspicions || []).map((item) => `<span>${Number(item.actorSeatNo)}号 → ${Number(item.primarySuspectSeatNo)}号</span>`).join('');
    const exchanges = (round.exchanges || []).map((item) => `<article><b>Q · ${Number(item.questionerSeatNo)}号 → ${Number(item.targetSeatNo)}号</b><p>${escapeHtml(item.question || '')}</p><b>A · ${Number(item.targetSeatNo)}号</b><p>${escapeHtml(item.response || '')}</p></article>`).join('');
    const defenses = (round.defenses || []).map((item) => `<article><b>${Number(item.seatNo)}号辩护</b><p>${escapeHtml(item.defense || '')}</p></article>`).join('');
    const votes = (round.voteRounds || []).map((voteRound, voteIndex) => `<div><b>${voteIndex ? '重投' : '首投'}</b>${voteRound.map((vote) => `<span>${Number(vote.voterSeatNo)}→${Number(vote.targetSeatNo)}</span>`).join('')}</div>`).join('');
    return `<details class="turing-replay-round" ${index === 0 ? 'open' : ''}><summary><span>第 ${Number(round.roundNo)} 轮</span><b>${escapeHtml(round.question || '')}</b><em>${round.eliminatedSeatNo ? `${Number(round.eliminatedSeatNo)}号出局` : '无人出局'}</em></summary><div><section><h3>匿名回答</h3><div class="turing-replay-answers">${answers || '<p>无记录</p>'}</div></section><section><h3>怀疑关系</h3><div class="turing-replay-links">${suspicions || '<span>无记录</span>'}</div></section><section><h3>质询回应</h3><div class="turing-replay-talk">${exchanges || '<p>无记录</p>'}</div></section><section><h3>公开辩护</h3><div class="turing-replay-talk">${defenses || '<p>无记录</p>'}</div></section><section><h3>投票流向</h3><div class="turing-replay-votes">${votes || '<p>无有效投票</p>'}</div></section></div></details>`;
  }).join('');
  const technicalRows = events.map((event) => `<article><i>${Number(event.sequenceNo || 0)}</i><div><b>${escapeHtml(event.eventType)}</b><span>第 ${Number(event.roundNo || 0)} 轮</span><code>${escapeHtml(JSON.stringify(event.payload || {}))}</code></div></article>`).join('');
  container.innerHTML = `<div class="replay-title"><div><span>图灵潜伏局复盘</span><h2>${escapeHtml(game.name)} · #${Number(game.id)}</h2><p>${escapeHtml(gameStatusLabel(game.status))}</p></div><b>${winner === 'HUMAN' ? '真人成功潜伏' : winner === 'AI' ? 'AI 成功识破' : '对局进行中'}</b></div><nav class="turing-replay-tabs"><button class="active" data-turing-replay-tab="overview">对局概览</button><button data-turing-replay-tab="rounds">逐轮过程</button><button data-turing-replay-tab="technical">技术日志</button></nav>
    <section class="turing-replay-pane active" data-turing-replay-pane="overview"><div class="turing-replay-overview"><header><span>EXPERIMENT RESULT</span><h3>${winner === 'HUMAN' ? '真人成功潜伏' : winner === 'AI' ? 'AI 成功识破真人' : '等待最终结算'}</h3><p>${escapeHtml(turingFinishedReasonLabel(game.finishedReason || recap.finishedReason))}</p></header><div class="replay-stats"><article><span>真人席位</span><b>${humanSeat ? `${Number(humanSeat)}号` : '隐藏'}</b></article><article><span>完成轮次</span><b>${Number(rounds.length || game.currentRound || 0)}</b></article><article><span>误淘汰 AI</span><b>${Number(recap.wrongAiEliminationCount || 0)}</b></article><article><span>淘汰顺序</span><b>${(recap.eliminationOrder || []).length ? recap.eliminationOrder.map((seat) => `${Number(seat)}号`).join(' → ') : '无人'}</b></article><article><span>最先识破真人</span><b>${recap.firstCorrectSuspectSeatNo ? `${Number(recap.firstCorrectSuspectSeatNo)}号` : '无人'}</b></article><article><span>判断最准确 AI</span><b>${recap.mostAccurateAiSeatNo ? `${Number(recap.mostAccurateAiSeatNo)}号` : '未产生'}</b></article><article><span>受误导最深 AI</span><b>${recap.mostMisledAiSeatNo ? `${Number(recap.mostMisledAiSeatNo)}号` : '未产生'}</b></article><article><span>公开事件</span><b>${Number(summary.eventCount || events.length)}</b></article></div><section><h3>身份、模型与人格</h3><div class="turing-replay-identities">${identityRows}</div></section><section><h3>关键观察</h3><div class="turing-replay-highlights">${highlights.slice(0, 6).map((item) => `<article><b>${escapeHtml(item.title)}</b><p>${escapeHtml(item.narration)}</p></article>`).join('') || '<p>暂无关键片段。</p>'}</div></section></div></section>
    <section class="turing-replay-pane" data-turing-replay-pane="rounds">${roundPanels || '<div class="real-empty">尚无已完成轮次。</div>'}</section>
    <section class="turing-replay-pane" data-turing-replay-pane="technical"><details class="turing-technical-log"><summary>展开公开技术事件（默认收起）</summary><div>${technicalRows || '<p>暂无事件。</p>'}</div></details></section>`;
  container.querySelectorAll('[data-turing-replay-tab]').forEach((button) => button.addEventListener('click', () => {
    const tab = button.dataset.turingReplayTab;
    container.querySelectorAll('[data-turing-replay-tab]').forEach((item) => item.classList.toggle('active', item === button));
    container.querySelectorAll('[data-turing-replay-pane]').forEach((pane) => pane.classList.toggle('active', pane.dataset.turingReplayPane === tab));
  }));
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
  const seatName = (seatNo) => {
    const player = players.find((item) => Number(item.seatNo) === Number(seatNo));
    return seatNo == null ? '未知席位' : `${Number(seatNo)}号${player?.displayName ? ` ${player.displayName}` : ''}`;
  };
  const turingActorSeat = event.payload?.actorSeatNo ?? event.payload?.questionerSeatNo
    ?? event.payload?.responderSeatNo ?? event.payload?.seatNo;
  const descriptions = {
    WOLF_TARGET_SELECTED: ['🌙', '狼人完成夜间选择', `${actorName} 已提交袭击目标，目标信息保持隐藏。`],
    GUARD_PROTECTED: ['🛡', '守卫完成守护', `${actorName} 已选择本夜守护目标。`],
    SEER_CHECKED: ['🔮', '预言家完成查验', `${actorName} 已获得一名玩家的阵营信息。`],
    WITCH_ACTION_COMPLETED: ['🧪', '女巫完成行动', `${actorName} 选择了 ${event.payload?.action || '行动'}。`],
    PLAYER_ELIMINATED: ['☠', '玩家出局', `${targetName || actorName} 因 ${event.payload?.reason || '游戏规则'} 被淘汰。`],
    PLAYER_SPOKE: ['💬', '玩家发言', `${actorName}：${event.payload?.message || '已完成公开发言。'}`],
    CONTROVERSY_IDENTIFIED: ['◎', '争议焦点形成', `系统依据公开怀疑与投票意向识别出 ${(event.payload?.controversialPlayers || []).length} 名争议玩家。`],
    PLAYER_QUESTIONED: ['?', '定向质询', `${actorName} 质询 ${targetName || '争议玩家'}：${event.payload?.publicSpeech || event.payload?.message || ''}`],
    PLAYER_RESPONDED: ['↩', '公开回应', `${actorName} 回应 ${targetName || '质询者'}：${event.payload?.publicSpeech || event.payload?.message || ''}`],
    PLAYER_FINAL_STATEMENT: ['◆', '最终陈述', `${actorName}：${event.payload?.publicSpeech || event.payload?.message || ''}`],
    PLAYER_VOTED: ['🗳', '玩家投票', `${actorName} 投票给 ${targetName || '目标玩家'}。`],
    VOTE_TIED: ['⚖', '投票平局', '本轮投票没有产生唯一放逐目标。'],
    VOTE_RESULT: ['◉', '投票结果', `最高票候选：${(event.payload?.candidateIds || []).map((id) => players.find((player) => player.id === id)?.displayName || `#${id}`).join('、') || '无'}。`],
    DAWN_RESULT: ['☀', event.payload?.peaceful ? '平安夜' : '天亮结算', event.payload?.peaceful ? '昨夜无人出局。' : `昨夜共有 ${(event.payload?.eliminatedIds || []).length} 名玩家出局。`],
    GAME_FINISHED: ['🏆', '对局结束', `获胜阵营：${event.payload?.winner || '待确认'}`],
    BATTLE_ACTION_EXECUTED: ['⚔', '生存行动', `${actorName} 完成了 ${battleActionLabel(event.payload?.action)}。`],
    BATTLE_ZONE_UPDATED: ['◎', '安全区收缩', `毒圈进入第 ${event.payload?.ringStage || '-'} 阶段，安全区域：${(event.payload?.safeRegions || []).join('、')}。`],
    BATTLE_ZONE_DAMAGE: ['☣', '受到毒圈伤害', `${actorName} 在 ${event.payload?.region || '圈外'} 受到 ${event.payload?.damage || 0} 点伤害，剩余生命 ${event.payload?.health ?? '-'}。`],
    BATTLE_SUPPLY_DROP: ['▣', '空投已抵达', `高级物资空投落在 ${event.payload?.region || '-'} 区，抵达后需要占领并守住。`],
    BATTLE_SUPPLY_CONTESTED: ['⚑', '开始占领空投', `${actorName} 正在 ${event.payload?.region || '-'} 区占领空投，还需守住 ${event.payload?.actionsRemaining || 0} 次行动。`],
    BATTLE_SUPPLY_CLAIM_BROKEN: ['◇', '空投占领中断', '占领者倒地、离开或隐藏，空投重新变为无主状态。'],
    BATTLE_SUPPLY_CAPTURED: ['▣', '空投占领成功', `${actorName} 守住空投并获得了高级武器、弹药、护甲和医疗包。`],
    BATTLE_PUBLIC_MESSAGE: ['💬', '公开消息', `${actorName}：${event.payload?.content || '发布了一条公开消息。'}`],
    BATTLE_LOCAL_MESSAGE: ['💬', '本地消息', `${actorName} 在 ${event.payload?.region || '当前'} 区发出消息：${event.payload?.content || ''}`],
    BATTLE_PRIVATE_MESSAGE: ['✉', '私聊', event.payload?.content
      ? `${actorName} → ${targetName || `#${event.payload?.targetPlayerId || '-'}`}：${event.payload.content}`
      : `${actorName} 向 ${targetName || `#${event.payload?.targetPlayerId || '-'}`} 发送了一条私密消息，正文对公开观战隐藏。`],
    BATTLE_RELATIONSHIP_CHANGED: ['◇', '关系变化', `${actorName} 对另一名玩家的主观看法发生明显变化。`],
    BATTLE_ALLIANCE_PROPOSED: ['◇', '联盟提议', `${actorName} 向另一名玩家提出临时联盟。`],
    BATTLE_ALLIANCE_FORMED: ['◆', '联盟成立', `玩家 #${event.payload?.memberAPlayerId || '-'} 与 #${event.payload?.memberBPlayerId || '-'} 正式建立临时联盟。`],
    BATTLE_ALLIANCE_REJECTED: ['◇', '联盟被拒绝', '一项联盟邀请被拒绝。'],
    BATTLE_ALLIANCE_LEFT: ['◇', '退出联盟', `${actorName} 公开退出了临时联盟。`],
    BATTLE_ALLIANCE_BETRAYED: ['⚠', '联盟背叛', `玩家 #${event.payload?.betrayerPlayerId || '-'} 攻击或处决了盟友 #${event.payload?.victimPlayerId || '-'}，联盟立即结束。`],
    BATTLE_PROMISE_PROPOSED: ['◇', '提出承诺', `${actorName} 提出了一项资源承诺，具体内容仅对知情者可见。`],
    BATTLE_PROMISE_ACCEPTED: ['◆', '承诺生效', '双方接受了一项资源承诺，仍需承诺者主动履行。'],
    BATTLE_PROMISE_FULFILLED: ['✓', '承诺履行', '一项资源承诺已经主动兑现。'],
    BATTLE_PROMISE_BROKEN: ['!', '承诺违约', '一项已接受的资源承诺逾期未履行。'],
    BATTLE_TRADE_PROPOSED: ['⇄', '交易提议', `${actorName} 发起了一项资源交易协商。`],
    BATTLE_TRADE_COMPLETED: ['⇄', '交易完成', '双方资源校验通过，交易已原子完成。'],
    BATTLE_TRADE_FAILED: ['!', '交易失败', '接受时资源或位置条件已变化，双方资源均未交换。'],
    BATTLE_SOCIAL_ACTION_REJECTED: ['!', '互动未完成', '局势变化使本次互动无法完成。'],
    BATTLE_HIDDEN_GOAL_COMPLETED: ['◆', '私人目标完成', `${actorName} 完成了一项目标并获得奖励，具体内容仍为私密信息。`],
    BATTLE_OBJECTIVE_CONTRIBUTED: ['⌁', '协作信标获得贡献', `${actorName} 为公共协作目标提交了物资。`],
    BATTLE_OBJECTIVE_COMPLETED: ['✦', '协作目标完成', '参与者共同完成信标建设并获得护甲奖励。'],
    BATTLE_OBJECTIVE_EXPIRED: ['!', '协作目标失败', '公共信标未能在截止轮次前获得足够物资。'],
    BATTLE_RUMOR_SHARED: ['≈', '传闻扩散', '一条未经证实、会随传播衰减可信度的消息正在扩散。'],
    BATTLE_INFORMATION_TRADE_PROPOSED: ['◇', '情报交易提议', `${actorName} 提出了一项私密情报交易。`],
    BATTLE_INFORMATION_TRADE_COMPLETED: ['◇', '情报交易完成', '付款与情报知晓权已原子交换，公开视角不展示条款。'],
    BATTLE_INFORMATION_TRADE_FAILED: ['!', '情报交易失败', '接受时条件已变化，付款与情报均未转移。'],
    BATTLE_PLAYER_DOWNED: ['!', '玩家倒地', `玩家 #${event.payload?.playerId || '-'} 已倒地，可被救援或处决。`],
    BATTLE_PLAYER_REVIVED: ['✚', '救援成功', `玩家 #${event.payload?.rescuerPlayerId || '-'} 救起了 #${event.payload?.playerId || '-'}。`],
    BATTLE_PLAYER_EXECUTED: ['☠', '倒地处决', `玩家 #${event.payload?.executorPlayerId || '-'} 处决了倒地的 #${event.payload?.playerId || '-'}。`],
    BATTLE_PLAYER_EXPOSED: ['!', '避战者暴露', `${actorName} 因连续避战被公开标记，位于 ${event.payload?.region || '-'} 区并将承受额外伤害。`],
    BATTLE_PLAYER_ELIMINATED: ['☠', '幸存者淘汰', `${targetName || actorName} 因 ${event.payload?.reason || '生存失败'} 被淘汰。`],
    BATTLE_FINISHED: ['🏆', '大逃杀结束', `${event.payload?.winnerName || targetName || '最终幸存者'} 获胜，生存评分 ${event.payload?.score ?? '-'}，共淘汰 ${event.payload?.kills ?? 0} 人。`],
    TURING_GAME_STARTED: ['⌁', '图灵潜伏局开始', `${event.payload?.playerCount || 6} 名匿名玩家进入实验，最多进行 ${event.payload?.maxRounds || 3} 轮。`],
    TURING_ROUND_STARTED: ['↻', `第 ${event.payload?.roundNo || event.roundNo} 轮开始`, '身份继续隐藏，所有存活玩家将回答同一道开放题。'],
    TURING_PROMPT_PUBLISHED: ['❖', '本轮问题发布', event.payload?.question || '等待本轮问题。'],
    TURING_ANSWER_SUBMITTED: ['…', '匿名回答已提交', '一名玩家已锁定回答；全部提交完成前不会公开内容与提交者。'],
    TURING_ANSWERS_REVEALED: ['▤', '匿名回答统一揭示', `本轮共公开 ${(event.payload?.answers || []).length} 份回答，现在可以比较表达风格。`],
    TURING_SUSPICION_SUBMITTED: ['?', '怀疑判断已提交', '一名玩家已锁定怀疑对象；全部提交完成前不会公开选择。'],
    TURING_CONTROVERSY_REVEALED: ['◎', '争议焦点揭示', `${(event.payload?.controversySeatNos || []).map(seatName).join('、') || '暂无单一焦点'} 成为本轮争议席位。`],
    TURING_QUESTION_ASKED: ['↗', '定向质询', `${seatName(event.payload?.questionerSeatNo)} 向 ${seatName(event.payload?.targetSeatNo)} 提问：${event.payload?.question || ''}`],
    TURING_RESPONSE_GIVEN: ['↩', '公开回应', `${seatName(event.payload?.responderSeatNo)} 回应 ${seatName(event.payload?.targetSeatNo)}：${event.payload?.response || ''}`],
    TURING_DEFENSE_GIVEN: ['◇', event.payload?.purpose === 'TIE_BREAK' ? '平票辩护' : '争议席位辩护', `${seatName(turingActorSeat)}：${event.payload?.defense || ''}`],
    TURING_VOTE_SUBMITTED: ['▣', '秘密投票已提交', '一名玩家已锁定选票；全部投票完成前不会公开去向。'],
    TURING_VOTES_REVEALED: ['🗳', event.payload?.revote ? '重投结果揭示' : '投票结果揭示', `本轮票型：${Object.entries(event.payload?.voteCounts || {}).map(([seat, count]) => `${seat}号 ${count} 票`).join(' · ') || '无有效票'}。`],
    TURING_VOTE_TIED: ['⚖', '投票出现平局', `${(event.payload?.candidateSeatNos || []).map(seatName).join('、')} 票数相同${event.payload?.revote ? '，本轮无人淘汰' : '，将先辩护再进行一次重投'}。`],
    TURING_PLAYER_ELIMINATED: ['☠', '匿名玩家被淘汰', `${seatName(event.payload?.seatNo)} 出局，身份揭示为 ${event.payload?.identity === 'HUMAN' ? '真人' : 'AI'}。`],
    TURING_IDENTITY_REVEALED: ['◉', '身份揭示', `${seatName(event.payload?.seatNo)} 的真实身份是 ${event.payload?.identity === 'HUMAN' ? '真人' : 'AI'}。`],
    TURING_ROUND_FINISHED: ['■', `第 ${event.payload?.roundNo || event.roundNo} 轮结束`, event.payload?.eliminatedSeatNo ? `${seatName(event.payload.eliminatedSeatNo)} 被淘汰；累计误淘汰 AI ${event.payload?.wrongAiEliminationCount || 0} 名。` : '平票重投后仍未形成唯一结果，本轮无人淘汰。'],
    TURING_GAME_FINISHED: ['🏆', '图灵潜伏局结束', `${event.payload?.winner === 'HUMAN' ? '真人成功潜伏' : 'AI 成功识破真人'} · ${turingFinishedReasonLabel(event.payload?.finishedReason)}`],
    TURING_PHASE_CHANGED: ['↻', '阶段推进', `${turingPhaseLabel(event.payload?.from)} → ${turingPhaseLabel(event.payload?.phase)}`]
  };
  const presentation = descriptions[event.eventType] || ['●', event.eventType, `${actorName} 完成了一次游戏行动。`];
  return presentation;
}

function werewolfRoleLabel(role) {
  return ({ WEREWOLF: '狼人', SEER: '预言家', WITCH: '女巫', GUARD: '守卫', VILLAGER: '平民' })[role] || role || '未知身份';
}

function werewolfPhaseLabel(phase) {
  return ({ NIGHT_WOLF: '狼人行动', NIGHT_GUARD: '守卫守护', NIGHT_SEER: '预言家查验', NIGHT_WITCH: '女巫用药', DAY_DISCUSSION: '公开陈述', DAY_QUESTION: '定向质询', DAY_RESPONSE: '争议回应', DAY_FINAL_STATEMENT: '最终陈述', DAY_VOTE: '放逐投票', FINISHED: '对局结束' })[phase] || phase || '等待阶段';
}

function gameModeLabel(mode) {
  return mode === 'BATTLE_ROYALE' ? 'AI 大逃杀' : mode === 'WEREWOLF' ? 'AI 狼人杀' : mode === 'TURING_HUNT' ? '图灵潜伏局' : mode;
}

function turingPhaseLabel(phase) {
  return ({ WAITING: '等待开始', ROUND_PROMPT: '问题发布', ANSWER_COLLECTION: '匿名回答', ANSWER_REVEAL: '回答揭示', SUSPICION_COLLECTION: '秘密怀疑', CONTROVERSY_REVEAL: '争议揭示', QUESTION: '定向质询', RESPONSE: '公开回应', DEFENSE: '辩护阶段', VOTE_COLLECTION: '秘密投票', VOTE_REVEAL: '统一揭票', ROUND_RESULT: '轮次结算', FINISHED: '身份揭晓' })[phase] || phase || '等待阶段';
}

function turingFinishedReasonLabel(reason) {
  return ({ HUMAN_ELIMINATED: '真人被投票淘汰', TWO_AIS_WRONGLY_ELIMINATED: 'AI 已误淘汰两名同伴', HUMAN_SURVIVED_THREE_ROUNDS: '真人成功坚持三轮', FINAL_TWO_SURVIVORS: '场上只剩真人与一名 AI，真人潜伏成功', FINAL_ROUND_TIE: '最终轮平票，真人潜伏成功' })[reason] || reason || '胜负已结算';
}

function gameStatusLabel(status) {
  return ({ WAITING: '等待开始', RUNNING: '对局进行中', FINISHED: '对局已结束', PAUSED: '已暂停', FAILED: '运行失败' })[status] || status;
}

function decisionSourceLabel(source, fallbackCode) {
  if (source === 'MODEL') return '模型决策';
  if (fallbackCode === 'RULE_ADJUSTED') return '系统策略接管';
  if (source === 'MODEL_FALLBACK') return '模型响应异常，本回合由系统策略继续';
  return '系统策略';
}

function decisionSourceMeta(decision = {}, advanced = false) {
  const label = decisionSourceLabel(decision.source, decision.fallbackCode);
  if (decision.fallbackCode === 'RULE_ADJUSTED') {
    return advanced && decision.adjustmentReason ? `${label} · ${decision.adjustmentReason}` : `${label} · 已选择合法行动继续对局`;
  }
  if (decision.source === 'MODEL_FALLBACK') return advanced && decision.fallbackCode
    ? `${label} · ${decision.fallbackCode}` : label;
  if (decision.protocolNormalized) return advanced
    ? `系统已规范化模型动作 · ${decision.normalizationKind || '兼容协议'}`
    : '系统已规范化模型动作';
  if (decision.source === 'MODEL') return `${label} · 模型自评置信度 ${decisionConfidenceLabel(decision.confidence)}（非胜率）`;
  return `${label} · 确定性规则行动`;
}

function decisionConfidenceLabel(value) {
  const numeric = Number(value || 0);
  return `${Math.round((numeric <= 1 ? numeric * 100 : numeric))}%`;
}

let selectedArenaPlayerId = null;
let selectedWerewolfRelation = 'SUSPECT';
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
  const activeActorId = game.currentActorPlayerId;
  const latestActorId = latestEvent?.actorPlayerId;
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
    const step = 360 / Math.max(players.length, 1);
    const angle = -90 + step / 2 + step * index;
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
  const actionFrom = seatPoints.get(String(latestActorId));
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
      <i class="stable-seat-avatar avatar-${Number(player.seatNo || 0) % 5}">${Number(player.seatNo)}</i><span class="wolf-seat-copy"><b>${escapeHtml(player.displayName)}</b><span>${escapeHtml(werewolfRoleLabel(player.roleCode))}</span><small>${escapeHtml(roleState)}</small><small class="seat-model-label">${escapeHtml(modelDisplayLabel(player))}</small></span>
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

function werewolfAudienceState(gameId) {
  const fallback = { suspectedWolfSeats: [], followedSeats: [], eliminationPredictionSeatNo: null, predictionRound: null };
  try {
    const parsed = JSON.parse(localStorage.getItem(`agentArenaAudience:${gameId}`) || 'null');
    if (!parsed || typeof parsed !== 'object') return fallback;
    return {
      suspectedWolfSeats: [...new Set((parsed.suspectedWolfSeats || []).map(Number).filter((seat) => seat >= 1 && seat <= 12))],
      followedSeats: [...new Set((parsed.followedSeats || []).map(Number).filter((seat) => seat >= 1 && seat <= 12))],
      eliminationPredictionSeatNo: Number(parsed.eliminationPredictionSeatNo) || null,
      predictionRound: Number(parsed.predictionRound) || null
    };
  } catch { return fallback; }
}

function updateWerewolfAudience(game, action, seatNo) {
  const state = werewolfAudienceState(game.id);
  const toggle = (values) => values.includes(seatNo) ? values.filter((seat) => seat !== seatNo) : [...values, seatNo];
  if (action === 'suspect' && game.status !== 'FINISHED') state.suspectedWolfSeats = toggle(state.suspectedWolfSeats);
  if (action === 'follow') state.followedSeats = toggle(state.followedSeats);
  if (action === 'predict' && game.status !== 'FINISHED' && game.currentPhase !== 'DAY_VOTE') {
    state.eliminationPredictionSeatNo = state.eliminationPredictionSeatNo === seatNo ? null : seatNo;
    state.predictionRound = Number(game.currentRound || 1);
  }
  localStorage.setItem(`agentArenaAudience:${game.id}`, JSON.stringify(state));
}

function werewolfRefText(refs = []) {
  return refs.length ? refs.map((ref) => `${Number(ref.seatNo ?? ref)}号`).join('、') : '暂无';
}

function renderWerewolfArenaV2(players, game, eventGroups, publicView = false) {
  const situation = samePlayerId(currentMatchProjection?.gameId, game.id)
    ? currentMatchProjection?.werewolfSituation || {}
    : { players: [], relationships: [], suspicionRanking: [], voteIntentDistribution: [] };
  const currentRound = Number(game.currentRound || 1);
  const audience = werewolfAudienceState(game.id);
  const playerBySeat = new Map(players.map((player) => [Number(player.seatNo), player]));
  const stanceBySeat = new Map((situation.players || []).map((stance) => [Number(stance.seatNo), stance]));
  const phaseOrder = ['NIGHT_WOLF', 'NIGHT_GUARD', 'NIGHT_SEER', 'NIGHT_WITCH', 'DAY_DISCUSSION', 'DAY_QUESTION', 'DAY_RESPONSE', 'DAY_FINAL_STATEMENT', 'DAY_VOTE'];
  const publicActionTypes = new Set(['PLAYER_SPOKE', 'PLAYER_QUESTIONED', 'PLAYER_RESPONDED', 'PLAYER_FINAL_STATEMENT']);
  const allPrimaryEvents = eventGroups.map((group) => group.primary).filter(Boolean);
  const isNightPhase = String(game.currentPhase || '').startsWith('NIGHT_');
  const latestPublic = [...allPrimaryEvents].reverse().find((event) => (
    publicActionTypes.has(event.eventType) && Number(event.roundNo || 0) === currentRound
  ));
  const latestVote = [...allPrimaryEvents].reverse().find((event) => event.eventType === 'PLAYER_VOTED' && Number(event.roundNo || 0) === currentRound);
  const focusEvent = isNightPhase ? null : game.currentPhase === 'DAY_VOTE' && latestVote ? latestVote : latestPublic;
  const thinking = isTurnInFlight() && game.status === 'RUNNING';
  const currentActor = findArenaPlayer(players, game.currentActorPlayerId);
  const focusActor = thinking ? currentActor : findArenaPlayer(players, focusEvent?.actorPlayerId) || currentActor;
  const focusTargetId = focusEvent?.payload?.targetPlayerId;
  const focusTarget = findArenaPlayer(players, focusTargetId);
  const focusStance = stanceBySeat.get(Number(focusActor?.seatNo));
  const focusSuspects = (focusEvent?.payload?.suspects || focusStance?.suspects || []).map((item) => typeof item === 'object' ? item : { seatNo: item });
  const focusSupports = (focusEvent?.payload?.supports || focusStance?.supports || []).map((item) => typeof item === 'object' ? item : { seatNo: item });
  const voteIntent = focusEvent?.payload?.voteIntentSeatNo ?? focusStance?.voteIntent?.seatNo ?? null;

  const votesByActor = new Map();
  allPrimaryEvents.forEach((event) => {
    if (event.eventType === 'PLAYER_VOTED' && Number(event.roundNo || 0) === currentRound) votesByActor.set(String(event.actorPlayerId), event.payload?.targetPlayerId);
  });
  const voteCounts = new Map();
  votesByActor.forEach((targetId) => { if (targetId != null) voteCounts.set(String(targetId), (voteCounts.get(String(targetId)) || 0) + 1); });
  const voteLeaders = [...voteCounts.entries()].sort((left, right) => right[1] - left[1]);
  const leaderId = voteLeaders[0]?.[0];

  const seatPoints = new Map();
  players.forEach((player, index) => {
    const angle = -90 + (360 / Math.max(players.length, 1)) * index;
    const radians = angle * Math.PI / 180;
    seatPoints.set(String(player.id), { left: 50 + Math.cos(radians) * 41, top: 57 + Math.sin(radians) * 27,
      x: 500 + Math.cos(radians) * 410, y: 410 + Math.sin(radians) * 194 });
  });

  let visibleRelations = [];
  if (selectedWerewolfRelation === 'SKILL') {
    visibleRelations = allPrimaryEvents.filter((event) => ['WOLF_TARGET_SELECTED', 'GUARD_PROTECTED', 'SEER_CHECKED', 'WITCH_ACTION_COMPLETED'].includes(event.eventType)
      && Number(event.roundNo || 0) === currentRound && event.payload?.targetPlayerId)
      .slice(-4).map((event) => ({ type: 'SKILL', fromPlayerId: event.actorPlayerId, toPlayerId: event.payload.targetPlayerId }));
  } else {
    const relationType = selectedWerewolfRelation;
    visibleRelations = (situation.relationships || []).filter((relation) => relation.type === relationType);
    if (relationType === 'VOTE' && !visibleRelations.length) {
      visibleRelations = (situation.relationships || []).filter((relation) => relation.type === 'VOTE_INTENT')
        .map((relation) => ({ ...relation, intent: true }));
      if (!visibleRelations.length) {
        visibleRelations = (situation.players || [])
          .filter((stance) => stance.voteIntent?.seatNo)
          .map((stance) => ({
            type: 'VOTE_INTENT',
            intent: true,
            from: { seatNo: stance.seatNo },
            to: { seatNo: stance.voteIntent.seatNo }
          }));
      }
    }
    visibleRelations = visibleRelations.map((relation) => ({ ...relation,
      fromPlayerId: playerBySeat.get(Number(relation.from?.seatNo))?.id,
      toPlayerId: playerBySeat.get(Number(relation.to?.seatNo))?.id }));
  }
  const relationIcon = ({ type, intent }) => intent ? '意' : ({ VOTE: '票', SUSPECT: '?', SUPPORT: '✓', SKILL: '✦' })[type] || '•';
  const relationLines = visibleRelations.map((relation) => {
    const from = seatPoints.get(String(relation.fromPlayerId));
    const to = seatPoints.get(String(relation.toPlayerId));
    if (!from || !to || relation.fromPlayerId === relation.toPlayerId) return '';
    const type = String(relation.type || selectedWerewolfRelation).toLowerCase();
    return `<g class="relation-${escapeHtml(type)} ${relation.intent ? 'is-intent' : ''}"><line x1="${from.x}" y1="${from.y}" x2="${to.x}" y2="${to.y}" marker-end="url(#relation-arrow-${escapeHtml(type)})"/><text x="${(from.x + to.x) / 2}" y="${(from.y + to.y) / 2}">${escapeHtml(relationIcon(relation))}</text></g>`;
  }).join('');

  const mutualConflict = (situation.relationships || []).find((relation) => relation.type === 'SUSPECT'
    && (situation.relationships || []).some((reverse) => reverse.type === 'SUSPECT'
      && Number(reverse.from?.seatNo) === Number(relation.to?.seatNo) && Number(reverse.to?.seatNo) === Number(relation.from?.seatNo)));
  const mostSuspected = situation.suspicionRanking?.[0];
  const phaseSteps = phaseOrder.map((phase) => `<span class="${phase === game.currentPhase ? 'active' : ''}"><i></i>${escapeHtml(werewolfPhaseLabel(phase))}</span>`).join('');
  const relationControls = [['VOTE', '票 当前投票'], ['SUSPECT', '? 怀疑'], ['SUPPORT', '✓ 支持'], ['SKILL', '✦ 技能']]
    .map(([value, label]) => `<button type="button" class="${selectedWerewolfRelation === value ? 'active' : ''}" data-wolf-relation="${value}">${label}</button>`).join('');

  let focusTitle = `${werewolfPhaseLabel(game.currentPhase)}`;
  let focusSpeech = focusEvent?.payload?.publicSpeech || focusEvent?.payload?.message || '公开信息正在形成，等待下一位玩家行动。';
  if (thinking && focusActor) {
    focusTitle = `${focusActor.seatNo}号 · ${focusActor.displayName} 正在分析`;
    focusSpeech = game.currentPhase === 'DAY_QUESTION' ? '正在组织一个针对公开矛盾的简短问题……'
      : game.currentPhase === 'DAY_RESPONSE' ? '正在核对质询内容并准备回应……' : '正在分析本轮公开发言与票型……';
  } else if (focusEvent?.eventType === 'PLAYER_QUESTIONED') {
    focusTitle = `${focusActor?.seatNo || '-'}号正在质询${focusTarget?.seatNo || '-'}号`;
  } else if (focusEvent?.eventType === 'PLAYER_RESPONDED') {
    focusTitle = `${focusActor?.seatNo || '-'}号回应${focusTarget?.seatNo || '-'}号`;
  } else if (focusEvent?.eventType === 'PLAYER_FINAL_STATEMENT') {
    focusTitle = `${focusActor?.seatNo || '-'}号 · ${focusActor?.displayName || '玩家'} 最终陈述`;
  } else if (focusEvent?.eventType === 'PLAYER_SPOKE') {
    focusTitle = `${focusActor?.seatNo || '-'}号 · ${focusActor?.displayName || '玩家'}`;
  } else if (focusEvent?.eventType === 'PLAYER_VOTED') {
    focusTitle = `${focusActor?.seatNo || '-'}号投票给${focusTarget?.seatNo || '-'}号`;
    focusSpeech = voteLeaders.length ? `当前领先：${findArenaPlayer(players, voteLeaders[0][0])?.seatNo || '-'}号，获得 ${voteLeaders[0][1]} 票。` : '等待首张正式投票。';
  }
  const focusMeta = focusActor ? `${modelDisplayLabel(focusActor)} · ${focusActor.agentProfileName || '座位默认人格'}` : (String(game.currentPhase || '').startsWith('NIGHT_') ? '夜间秘密行动保持隐藏' : '等待行动者');

  const seats = players.map((player) => {
    const point = seatPoints.get(String(player.id));
    const stance = stanceBySeat.get(Number(player.seatNo));
    const primarySuspect = stance?.suspects?.[0];
    const active = samePlayerId(player.id, game.currentActorPlayerId);
    const target = samePlayerId(player.id, focusTargetId);
    const leader = samePlayerId(player.id, leaderId);
    const roleCamp = !player.roleCode ? 'unknown' : player.roleCode === 'WEREWOLF' ? 'wolf' : player.roleCode === 'VILLAGER' ? 'villager' : 'god';
    return `<button type="button" class="wolf-seat wolf-seat-v2 role-${roleCamp} ${player.status === 'ALIVE' ? 'alive' : 'eliminated'} ${active ? 'is-acting' : ''} ${target ? 'is-question-target' : ''} ${leader ? 'is-vote-leader' : ''} ${samePlayerId(player.id, selectedArenaPlayerId) ? 'is-selected' : ''}" style="left:${point.left}%;top:${point.top}%" data-arena-player="${Number(player.id)}">
      <i class="stable-seat-avatar avatar-${Number(player.seatNo || 0) % 5}">${escapeHtml(player.agentAvatarEmoji || String(player.seatNo))}</i><span class="seat-no">${Number(player.seatNo)}号</span><span class="wolf-seat-copy"><b>${escapeHtml(player.displayName)}</b><span>${escapeHtml(modelDisplayLabel(player))}</span><small>${escapeHtml(player.agentProfileName || '座位默认人格')}</small><small class="seat-primary-suspect">怀疑：${primarySuspect ? `${Number(primarySuspect.seatNo)}号 ${escapeHtml(primarySuspect.displayName || '')}` : '暂未表态'}</small></span>
      <em>${player.status === 'ALIVE' ? active ? '行动中' : target ? '被质询' : leader ? '票数领先' : '存活' : '已出局'}</em>
      ${voteCounts.get(String(player.id)) ? `<span class="wolf-vote-count">${voteCounts.get(String(player.id))} 票</span>` : ''}
      ${audience.suspectedWolfSeats.includes(Number(player.seatNo)) ? '<span class="audience-mark suspect">我怀疑</span>' : ''}${audience.followedSeats.includes(Number(player.seatNo)) ? '<span class="audience-mark follow">★</span>' : ''}
    </button>`;
  }).join('');

  const relationshipSummary = `<div class="wolf-situation-summary"><span>最受怀疑 <b>${mostSuspected ? `${Number(mostSuspected.player?.seatNo)}号 · ${Number(mostSuspected.count)}人` : '尚未形成'}</b></span><span>直接冲突 <b>${mutualConflict ? `${Number(mutualConflict.from.seatNo)}号 ↔ ${Number(mutualConflict.to.seatNo)}号` : '暂无'}</b></span><span>投票意向 <b>${situation.voteIntentDistribution?.[0] ? `${Number(situation.voteIntentDistribution[0].player?.seatNo)}号 · ${Number(situation.voteIntentDistribution[0].count)}人` : '分散'}</b></span></div>`;
  const audienceSummary = `<div class="audience-summary"><span>个人观战标记</span><b>我怀疑：${audience.suspectedWolfSeats.length ? audience.suspectedWolfSeats.map((seat) => `${seat}号`).join('、') : '未标记'}</b><b>关注：${audience.followedSeats.length ? audience.followedSeats.map((seat) => `${seat}号`).join('、') : '未标记'}</b><b>本轮预测：${audience.eliminationPredictionSeatNo ? `${audience.eliminationPredictionSeatNo}号出局` : '未选择'}</b></div>`;
  return `<div class="werewolf-arena werewolf-arena-v2 ${String(game.currentPhase || '').startsWith('NIGHT_') ? 'night-scene' : 'day-scene'}">
    <div class="arena3d-host" data-arena-3d="werewolf" aria-label="可旋转的 3D 狼人杀推理圆桌"></div>
    <div class="wolf-phase-track">${phaseSteps}</div>${relationshipSummary}
    <div class="wolf-relation-switch" role="group" aria-label="圆桌关系类型">${relationControls}</div>
    <svg class="wolf-links" viewBox="0 0 1000 720" preserveAspectRatio="none" aria-hidden="true"><defs>
      <marker id="relation-arrow-vote" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z"/></marker><marker id="relation-arrow-vote_intent" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z"/></marker>
      <marker id="relation-arrow-suspect" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z"/></marker><marker id="relation-arrow-support" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z"/></marker><marker id="relation-arrow-skill" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z"/></marker>
    </defs>${relationLines}</svg>
    <div class="wolf-table"><div class="wolf-table-ring"></div><div class="wolf-center" aria-live="polite"><small>第 ${currentRound} 天 · ${escapeHtml(werewolfPhaseLabel(game.currentPhase))}</small><h3>${escapeHtml(focusTitle)}</h3><span class="focus-agent-meta">${escapeHtml(focusMeta)}</span><blockquote>“${escapeHtml(focusSpeech)}”</blockquote><div class="focus-relations"><span>? 怀疑：${escapeHtml(werewolfRefText(focusSuspects))}</span><span>✓ 支持：${escapeHtml(werewolfRefText(focusSupports))}</span><span>票 投票意向：${voteIntent ? `${Number(voteIntent)}号` : '暂无'}</span></div>${thinking ? '<div class="focus-thinking"><i></i>模型正在生成并校验合法行动</div>' : ''}</div></div>
    ${seats}${audienceSummary}<div class="god-view-note">${publicView && game.status !== 'FINISHED' ? '公开观战 · 隐藏身份与夜间秘密' : '房主视角 · 私有技能仅在授权日志中可见'} · 点击席位打开详情</div>
  </div>`;
}

function renderWerewolfEventCard(group, event, players, index) {
  const info = describeGameEvent(group, players);
  const actor = players.find((player) => player.id === event.actorPlayerId);
  const decision = group.decision?.payload || {};
  const targetId = event.payload?.targetPlayerId || decision.targetPlayerId || event.payload?.playerId;
  const target = players.find((player) => player.id === targetId);
  const message = ['PLAYER_SPOKE', 'PLAYER_QUESTIONED', 'PLAYER_RESPONDED', 'PLAYER_FINAL_STATEMENT'].includes(event.eventType)
    ? (event.payload?.publicSpeech || event.payload?.message) : null;
  const strategyLabel = decision.selfRole
    ? `私有真实策略 · 身份已校验为${werewolfRoleLabel(decision.selfRole)}`
    : '策略摘要';
  const strategySummary = decision.summary
    || decision.reasonSummary
    || (decision.summaryHidden ? '真实策略摘要可能包含隐藏身份，对局中不公开。' : '未提供策略说明');
  return `<article class="werewolf-event-card ${index === 0 ? 'latest' : ''}">
    <header><i>${escapeHtml(info[0])}</i><div><small>${actor ? `${Number(actor.seatNo)}号 · ${escapeHtml(actor.displayName)} · ${escapeHtml(modelDisplayLabel(actor))}` : '系统事件'}</small><b>${escapeHtml(info[1])}</b></div><em>#${Number(event.sequenceNo || 0)}</em></header>
    ${message ? `<blockquote>“${escapeHtml(message)}”</blockquote>` : `<p>${escapeHtml(info[2])}${target ? ` 目标：${target.seatNo}号 ${escapeHtml(target.displayName)}` : ''}</p>`}
    ${group.decision ? `<div class="wolf-decision"><span>${escapeHtml(strategyLabel)}</span><p><b>${escapeHtml(decision.action || 'ACTION')}</b>${decision.targetSeatNo ? ` → ${Number(decision.targetSeatNo)}号` : ''} · ${escapeHtml(strategySummary)}</p>${decision.reasonTags?.length ? `<small>${decision.reasonTags.map((tag) => escapeHtml(tag)).join(' · ')}</small>` : ''}<em>${escapeHtml(decisionSourceMeta(decision, selectedReportTab === 'logs'))}</em></div>` : ''}
    <footer><span>第 ${Number(event.roundNo || 0)} 轮</span><span>${escapeHtml(werewolfPhaseLabel(event.phase))}</span>${group.phaseChangedTo ? `<b>→ ${escapeHtml(werewolfPhaseLabel(group.phaseChangedTo))}</b>` : ''}</footer>
  </article>`;
}

function battleActionLabel(action) {
  const labels = { MOVE: '移动', SEARCH: '搜索物资', CAPTURE: '占领空投', REST: '休整', HIDE: '隐蔽', HEAL: '使用医疗包', GUARD: '加固防具', TRADE: '交易', ATTACK: '发起攻击', REVIVE: '救援倒地玩家', EXECUTE: '处决倒地玩家', SKIP: '暂不行动' };
  return labels[action] || action || '一次行动';
}

function battleLootLabel(type, amount, name) {
  const labels = { WEAPON: name || '武器', WEAPON_UPGRADE: `${name || '武器'}升级`, AMMO: `弹药 +${amount}`, FOOD: `食物 +${amount}`, WATER: `水 +${amount}`, MEDKIT: `医疗包 +${amount}`, ARMOR: `护甲 +${amount}`, EMPTY: '没有可用物资' };
  return labels[type] || '未知物资';
}

function battleActionResult(payload, target) {
  if (payload.action === 'ATTACK' && target) return `攻击了 ${target.displayName}，造成 ${payload.damage ?? 0} 点伤害${payload.critical ? '（暴击）' : ''}，对方生命降至 ${payload.targetHealth ?? '-'}。`;
  if (payload.action === 'SEARCH') return payload.lootType === 'EMPTY' ? '物资点已经枯竭，或搜索被同区敌人干扰。' : `搜索成功：获得${battleLootLabel(payload.lootType, payload.lootAmount, payload.lootName)}。`;
  if (payload.action === 'CAPTURE') return '开始占领无人把守的空投，倒地、离开或隐藏都会中断占领。';
  if (payload.action === 'MOVE') return `已移动至 ${payload.region || '新区域'}。`;
  if (payload.action === 'HEAL') return `消耗医疗包恢复生命，当前生命 ${payload.health ?? '-'}。`;
  if (payload.action === 'GUARD') return `完成防御准备，当前护甲 ${payload.armor ?? 0}。`;
  if (payload.action === 'REST') return `原地休整恢复状态，当前生命 ${payload.health ?? '-'}。`;
  if (payload.action === 'HIDE') return '进入隐蔽状态，遭受攻击时可降低伤害。';
  if (payload.action === 'REVIVE' && target) return `消耗医疗包救起 ${target.displayName}，对方恢复至 ${payload.targetHealth ?? 30} 点生命。`;
  if (payload.action === 'EXECUTE' && target) return `处决了倒地的 ${target.displayName}。`;
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
    <div class="battle-action-main"><i>⚔</i><div><small>${escapeHtml(actorName)} · ${escapeHtml(modelDisplayLabel(actor || {}))}</small><h3>${escapeHtml(action)}</h3><p>${escapeHtml(result)}</p></div></div>
    <div class="survival-stats"><span><i>♥</i>生命 <b>${payload.health ?? '-'}</b></span><span><i>◈</i>食物 <b>${payload.food ?? '-'}</b></span><span><i>◌</i>水 <b>${payload.water ?? '-'}</b></span><span><i>⬡</i>护甲 <b>${payload.armor ?? 0}</b></span><span><i>✚</i>医疗 <b>${payload.medkits ?? 0}</b></span>${payload.armed ? `<span class="armed"><i>⚔</i>${escapeHtml(payload.weaponName || '武器')} Lv.${payload.weaponLevel || 1} · ${payload.ammo ?? 0} 发</span>` : ''}<span><i>☠</i>淘汰 <b>${payload.kills ?? 0}</b></span></div>
    ${group.decision ? `<div class="decision-reason"><span>选择理由</span><p>${escapeHtml(decision.summary || '未提供策略说明')}</p><em>${escapeHtml(decisionSourceMeta(decision))}</em></div>` : ''}
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
  const world = window.BattleWorld.resolve(game, eventGroups.flatMap(group => [group.primary, group.decision].filter(Boolean)));
  const { ringStage, safeRegions } = world;
  Object.entries(world.regionProfiles).forEach(([region, profile]) => {
    if (regionMeta[region]) regionMeta[region] = [profile.name, regionMeta[region][1], profile.resources];
  });
  const nextRemovedRegion = world.ringOrder.length && ringStage < 5 ? world.ringOrder[world.ringOrder.length - ringStage] : null;
  const supplyDropRegion = world.supplyDropRegion;
  const latestGroup = latestCompletedGroup(eventGroups);
  const latestEvent = latestGroup?.primary || null;
  const latestInfo = latestEvent ? describeGameEvent(latestGroup, players) : ['◌', '等待首个行动', '推进对局后，移动、交战、搜索与毒圈变化会直接显示在地图上。'];
  const activeActorId = game.currentActorPlayerId;
  const latestActorId = latestEvent?.actorPlayerId;
  const targetId = latestEvent?.payload?.targetPlayerId ?? latestEvent?.payload?.playerId;
  const actor = findArenaPlayer(players, latestActorId);
  const target = findArenaPlayer(players, targetId);
  const action = latestEvent?.eventType === 'BATTLE_ACTION_EXECUTED' ? latestEvent.payload?.action : latestEvent?.eventType;
  const actionGlyphs = { MOVE: '➜', SEARCH: '⌕', CAPTURE: '⚑', REST: '♥', HIDE: '◐', HEAL: '✚', GUARD: '⬡', TRADE: '⇄', ATTACK: '⚔', REVIVE: '✚', EXECUTE: '☠', SKIP: '◌', BATTLE_ZONE_UPDATED: '◎', BATTLE_ZONE_DAMAGE: '☣', BATTLE_SUPPLY_DROP: '▣', BATTLE_SUPPLY_CONTESTED: '⚑', BATTLE_SUPPLY_CAPTURED: '▣', BATTLE_PLAYER_DOWNED: '!', BATTLE_PLAYER_REVIVED: '✚', BATTLE_PLAYER_EXECUTED: '☠', BATTLE_PLAYER_EXPOSED: '!', BATTLE_PLAYER_ELIMINATED: '☠', BATTLE_FINISHED: '🏆' };
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
      && samePlayerId(event.actorPlayerId, latestActorId)
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
      const downed = player.status === 'DOWNED';
      const active = samePlayerId(player.id, activeActorId);
      const targeted = samePlayerId(player.id, targetId);
      const selected = samePlayerId(player.id, selectedArenaPlayerId);
      const health = Math.max(0, Math.min(100, Number(player.health ?? 0)));
      return `<button type="button" class="map-player ${alive ? 'alive' : downed ? 'downed' : 'eliminated'} ${player.hidden ? 'hidden' : ''} ${player.exposed ? 'is-targeted' : ''} ${active ? 'is-acting' : ''} ${targeted ? 'is-targeted' : ''} ${selected ? 'is-selected' : ''} ${health <= 30 && (alive || downed) ? 'is-critical' : ''}" data-arena-player="${Number(player.id)}" aria-label="聚焦 ${escapeHtml(player.displayName)}">
        <span class="map-player-token avatar-${Number(player.seatNo || 0) % 5}"><i>${Number(player.seatNo)}</i>${active ? '<em>…</em>' : ''}</span><span class="map-player-copy"><b>${escapeHtml(player.displayName)}${downed ? ' · 倒地' : player.exposed ? ' · 已暴露' : ''}</b><small>${downed ? '等待救援或处决' : player.hidden ? '隐蔽中' : player.weaponName ? `${escapeHtml(player.weaponName)} Lv.${Number(player.weaponLevel || 1)} · ${Number(player.ammo || 0)} 发` : '徒手探索'}</small><small class="seat-model-label">${escapeHtml(modelDisplayLabel(player))}</small><span class="map-health-track"><i style="width:${health}%"></i></span></span>
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
    <div class="arena3d-host" data-arena-3d="battle" aria-label="可旋转的 3D 林地生存战术地图"></div>
    <div class="map-grid-lines"></div>
    <div class="battle-storm-vignette"></div>
    <svg class="battle-action-layer" viewBox="0 0 1000 720" preserveAspectRatio="none" aria-hidden="true"><defs><marker id="battle-trace-arrow" markerWidth="9" markerHeight="9" refX="8" refY="4.5" orient="auto"><path d="M0,0 L9,4.5 L0,9 Z" /></marker></defs>${actionTrace}</svg>
    <div class="map-ring-status"><span>${world.known ? `安全区监测 · ${ringStage}/5` : '安全区 · 等待服务端状态'}</span><b>${aliveCount} 人存活</b><div class="ring-meter">${Array.from({ length: 5 }, (_, index) => `<i class="${index < ringStage ? 'active' : ''}"></i>`).join('')}</div><small>${world.nextShrinkRound ? `第 ${world.nextShrinkRound} 轮再次缩圈` : world.known && ringStage === 5 ? '最终安全区' : '缩圈预告待同步'} · ${armedCount} 人持械</small><small>安全区域：${world.known ? escapeHtml(safeRegions.join('、')) : '待同步'}</small></div>
    <div class="battle-live-feed action-${String(action || 'waiting').toLowerCase()}" aria-live="polite"><i>${escapeHtml(actionGlyph)}</i><div><small>第 ${Number(latestEvent?.roundNo || game.currentRound || 1)} 轮 · #${Number(latestEvent?.sequenceNo || 0)}</small><b>${escapeHtml(actionTitle)}</b><p>${escapeHtml(actionDetail)}</p></div></div>
    ${zones}
    ${hiddenPlayers.length ? `<section class="map-zone hidden-zone"><header><i>?</i><div><b>隐蔽区域</b><small>${hiddenPlayers.length} 名玩家位置已隐藏</small></div></header><div class="zone-players">${hiddenPlayers.map((player) => `<button type="button" class="map-player alive hidden ${samePlayerId(player.id, selectedArenaPlayerId) ? 'is-selected' : ''}" data-arena-player="${Number(player.id)}"><span class="map-player-token"><i>${Number(player.seatNo)}</i></span><span class="map-player-copy"><b>${escapeHtml(player.displayName)}</b><small>位置保密</small></span></button>`).join('')}</div></section>` : ''}
    <div class="map-legend"><span><i class="alive-dot"></i>存活</span><span><i class="safe-dot"></i>安全区</span><span><i class="closing-dot"></i>即将缩圈</span><span><i class="supply-dot"></i>空投</span><span>点击玩家可聚焦</span></div>
  </div><details class="battle-rules-panel"><summary>行动取舍与胜负规则</summary><p>${escapeHtml(game.battleWorld?.victoryRule || '最后未淘汰者获胜；限时或全员倒地时按生存积分结算。')}</p><p>每次存活玩家的战术行动消耗食物和饮水各1；任一归零受到5伤害。防守仅减免下一次受击8点伤害，不增加永久护甲，下次行动失效。攻击、占领、处决和非林地搜索会暴露自己，承受额外8点原始伤害，安静行动后解除。连续第3次及以后隐藏会被侦测并暴露位置，换用其他战术动作后重置。</p><div>${Object.entries(world.regionProfiles).map(([region, profile]) => `<span>${region} · ${escapeHtml(profile.name)}：${profile.coverReduction ? `掩护减伤${profile.coverReduction}` : profile.restBonus ? `休整额外恢复${profile.restBonus}` : escapeHtml(profile.resources)}</span>`).join('')}</div><p>移动路线：${Object.entries(world.topology).map(([region, neighbors]) => `${escapeHtml(region)}→${escapeHtml(neighbors.join(" / "))}`).join("；")}。</p><p>生存积分：生命×2 + 护甲 + 食物×2 + 饮水×2 + 医疗包×12 + 武器等级×18 + 弹药 + 击杀×120 + 计分有效伤害×2。每个目标累计最多贡献100计分伤害，过量伤害不计分，治疗与救援不重置。</p></details>`;
}

let autoPlayEnabled = false;
let autoPlayTimer = null;
let autoPlayDelay = 800;
let autoPlaySteps = 0;
let autoPlayRetryCount = 0;
let autoPlayInFlight = false;
let manualStepInFlight = false;
let autoPlayStatus = '';
let autoPlayRetrying = false;
const AUTO_PLAY_STEP_LIMIT = 1000;

function isTurnInFlight() {
  return autoPlayInFlight || manualStepInFlight;
}

function updateAutoPlayControls() {
  const advanceButton = document.querySelector('.real-advance');
  const autoButton = document.querySelector('.real-auto');
  const status = document.querySelector('.auto-run-status');
  const turingMode = lastRealGame?.mode === 'TURING_HUNT';
  const waitingForHuman = turingMode && lastRealGame?.humanActionRequired === true;
  if (advanceButton) {
    advanceButton.disabled = waitingForHuman || autoPlayEnabled || isTurnInFlight();
    advanceButton.textContent = waitingForHuman ? '等待你操作'
      : isTurnInFlight() ? (turingMode ? '阶段处理中…' : '模型行动中…')
        : turingMode ? '推进阶段' : '推进一步';
  }
  if (autoButton) {
    autoButton.disabled = waitingForHuman || manualStepInFlight;
    autoButton.textContent = waitingForHuman ? '请先提交' : autoPlayEnabled
      ? (turingMode ? '暂停自动运行' : autoPlayInFlight ? '暂停模拟 · 行动中' : '暂停模拟')
      : manualStepInFlight ? (turingMode ? '阶段处理中' : '单步执行中')
        : turingMode ? '自动运行' : '自动模拟';
  }
  if (status) {
    status.textContent = autoPlayStatus;
    status.classList.toggle('retrying', autoPlayRetrying);
    status.hidden = !autoPlayStatus;
  }
  const thinking = document.querySelector('.thinking-indicator');
  if (thinking) {
    thinking.classList.toggle('active', isTurnInFlight());
    const elapsed = thinking.querySelector('.thinking-elapsed');
    thinking.childNodes.forEach((node) => {
      if (node.nodeType === Node.TEXT_NODE && node.textContent.trim()) node.textContent = isTurnInFlight() ? '思考中' : '就绪';
    });
    if (elapsed && !isTurnInFlight()) elapsed.hidden = true;
  }
  updateThinkingClock();
}

function stopAutoPlay(status = '') {
  autoPlayEnabled = false;
  clearTimeout(autoPlayTimer);
  autoPlayTimer = null;
  autoPlayRetryCount = 0;
  autoPlayRetrying = false;
  autoPlayStatus = status;
  updateAutoPlayControls();
}

function isRecoverableAutoPlayError(error) {
  if (!error) return false;
  if (error.code === 'G3002' || [401, 403, 404].includes(Number(error.status))) return false;
  return !error.status || [408, 409, 425, 429, 500, 502, 503, 504].includes(Number(error.status));
}

function autoPlayBackoff(error, retryCount) {
  const base = Number(error?.status) === 429 ? 3000 : 1200;
  return Math.min(30000, base * (2 ** Math.min(Math.max(retryCount - 1, 0), 4)));
}

async function runAutoStep() {
  if (!autoPlayEnabled || autoPlayInFlight) return;
  if (autoPlaySteps >= AUTO_PLAY_STEP_LIMIT) {
    stopAutoPlay(`已达到 ${AUTO_PLAY_STEP_LIMIT} 步安全上限`);
    showToast(`自动运行已达到 ${AUTO_PLAY_STEP_LIMIT} 步安全上限，请检查对局状态`);
    return;
  }
  autoPlayInFlight = true;
  autoPlayRetrying = false;
  autoPlayStatus = `自动推进 · 正在执行第 ${autoPlaySteps + 1} 步`;
  updateAutoPlayControls();

  const outcome = await advanceCurrentGame(true);
  autoPlayInFlight = false;
  if (!autoPlayEnabled) {
    autoPlayStatus = '自动模拟已暂停';
    updateAutoPlayControls();
    return;
  }
  if (outcome?.continued) {
    autoPlaySteps += 1;
    autoPlayRetryCount = 0;
    autoPlayStatus = `自动推进 · 已完成 ${autoPlaySteps} 步`;
    updateAutoPlayControls();
    autoPlayTimer = setTimeout(runAutoStep, autoPlayDelay);
    return;
  }
  if (outcome?.finished) {
    stopAutoPlay('对局已结束');
    return;
  }
  if (outcome?.humanRequired) {
    stopAutoPlay('等待你完成本阶段操作');
    showToast('自动模拟已暂停：轮到你操作');
    return;
  }
  if (isRecoverableAutoPlayError(outcome?.error)) {
    autoPlayRetryCount += 1;
    const retryDelay = autoPlayBackoff(outcome.error, autoPlayRetryCount);
    autoPlayRetrying = true;
    autoPlayStatus = `${outcome.error.status === 429 ? '请求频率受限' : '服务暂时波动'}，${Math.ceil(retryDelay / 1000)} 秒后自动重试`;
    updateAutoPlayControls();
    if (autoPlayRetryCount === 1) showToast(`${autoPlayStatus}，无需重新点击`);
    autoPlayTimer = setTimeout(runAutoStep, retryDelay);
    return;
  }
  const reason = outcome?.error?.message || '对局当前不可继续推进';
  stopAutoPlay(`自动模拟已暂停：${reason}`);
  showToast(`自动模拟已暂停：${reason}`);
}

function toggleAutoPlay() {
  if (autoPlayEnabled) {
    stopAutoPlay(autoPlayInFlight ? '已请求暂停，当前行动完成后停止' : '自动模拟已暂停');
    if (!autoPlayInFlight) loadCurrentGame();
    return;
  }
  autoPlayEnabled = true;
  autoPlaySteps = 0;
  autoPlayRetryCount = 0;
  autoPlayRetrying = false;
  autoPlayStatus = autoPlayInFlight ? '当前行动完成后继续自动模拟' : '自动模拟已启动';
  clearTimeout(autoPlayTimer);
  updateAutoPlayControls();
  if (!autoPlayInFlight) runAutoStep();
}

const KEY_EVENT_TYPES = new Set([
  'PLAYER_ELIMINATED', 'PLAYER_SPOKE', 'PLAYER_QUESTIONED', 'PLAYER_RESPONDED', 'CONTROVERSY_IDENTIFIED', 'VOTE_RESULT', 'VOTE_TIED', 'DAWN_RESULT', 'GAME_FINISHED',
  'WOLF_TARGET_SELECTED', 'GUARD_PROTECTED', 'SEER_CHECKED', 'WITCH_ACTION_COMPLETED',
  'BATTLE_PLAYER_ELIMINATED', 'BATTLE_PLAYER_EXPOSED', 'BATTLE_ZONE_UPDATED', 'BATTLE_SUPPLY_DROP',
  'BATTLE_SUPPLY_CONTESTED', 'BATTLE_SUPPLY_CLAIM_BROKEN', 'BATTLE_SUPPLY_CAPTURED', 'BATTLE_ZONE_DAMAGE', 'BATTLE_FINISHED',
  'BATTLE_PUBLIC_MESSAGE', 'BATTLE_LOCAL_MESSAGE', 'BATTLE_PRIVATE_MESSAGE', 'BATTLE_RELATIONSHIP_CHANGED',
  'BATTLE_ALLIANCE_PROPOSED', 'BATTLE_ALLIANCE_FORMED', 'BATTLE_ALLIANCE_REJECTED', 'BATTLE_ALLIANCE_LEFT', 'BATTLE_ALLIANCE_BETRAYED',
  'BATTLE_PROMISE_PROPOSED', 'BATTLE_PROMISE_ACCEPTED', 'BATTLE_PROMISE_FULFILLED', 'BATTLE_PROMISE_BROKEN',
  'BATTLE_TRADE_PROPOSED', 'BATTLE_TRADE_COMPLETED', 'BATTLE_TRADE_FAILED', 'BATTLE_SOCIAL_ACTION_REJECTED',
  'BATTLE_HIDDEN_GOAL_COMPLETED', 'BATTLE_OBJECTIVE_CONTRIBUTED', 'BATTLE_OBJECTIVE_COMPLETED', 'BATTLE_OBJECTIVE_EXPIRED',
  'BATTLE_RUMOR_SHARED', 'BATTLE_INFORMATION_TRADE_PROPOSED', 'BATTLE_INFORMATION_TRADE_COMPLETED', 'BATTLE_INFORMATION_TRADE_FAILED',
  'BATTLE_PLAYER_DOWNED', 'BATTLE_PLAYER_REVIVED', 'BATTLE_PLAYER_EXECUTED',
  'TURING_PROMPT_PUBLISHED', 'TURING_ANSWERS_REVEALED', 'TURING_CONTROVERSY_REVEALED', 'TURING_QUESTION_ASKED',
  'TURING_RESPONSE_GIVEN', 'TURING_DEFENSE_GIVEN', 'TURING_VOTES_REVEALED', 'TURING_VOTE_TIED',
  'TURING_PLAYER_ELIMINATED', 'TURING_IDENTITY_REVEALED', 'TURING_ROUND_FINISHED', 'TURING_GAME_FINISHED'
]);

function eventImportance(group) {
  const event = group.primary || group.decision;
  if (!event) return 'normal';
  if (['GAME_FINISHED', 'BATTLE_FINISHED', 'TURING_GAME_FINISHED', 'PLAYER_ELIMINATED', 'BATTLE_PLAYER_ELIMINATED', 'TURING_PLAYER_ELIMINATED'].includes(event.eventType)
      || event.payload?.critical || Number(event.payload?.damage || 0) >= 45) return 'critical';
  if (group.phaseChangedTo && ['NIGHT_WOLF', 'DAY_DISCUSSION'].includes(group.phaseChangedTo)) return 'key';
  if (KEY_EVENT_TYPES.has(event.eventType) || event.payload?.action === 'ATTACK') return 'key';
  return 'normal';
}

function reportGroupsForTab(groups, tab) {
  if (tab === 'decisions') return groups.filter((group) => group.decision).slice(-30).reverse();
  if (tab === 'logs') return groups.slice(-50).reverse();
  const commentary = groups.filter((group) => {
    const event = group.primary || group.decision;
    return event && (eventImportance(group) !== 'normal'
      || ['PLAYER_SPOKE', 'PLAYER_QUESTIONED', 'PLAYER_RESPONDED', 'PLAYER_FINAL_STATEMENT', 'PLAYER_VOTED'].includes(event.eventType)
      || (event.eventType === 'BATTLE_ACTION_EXECUTED' && ['ATTACK', 'MOVE', 'HEAL'].includes(event.payload?.action)));
  });
  return (commentary.length ? commentary : groups).slice(-18).reverse();
}

function renderReportCards(groups, players, isBattle) {
  return groups.map((group, index) => {
    const event = group.primary || group.decision;
    if (!event) return '';
    if (isBattle && event.eventType === 'BATTLE_ACTION_EXECUTED') return renderBattleActionCard(group, event, players, index);
    if (!isBattle) return renderWerewolfEventCard(group, event, players, index);
    const info = describeGameEvent(group, players);
    return `<article class="action-event-card ${index === 0 ? 'latest' : ''} importance-${eventImportance(group)}"><i>${escapeHtml(info[0])}</i><div><b>${escapeHtml(info[1])}</b><p>${escapeHtml(info[2])}</p><span>第 ${Number(event.roundNo || 0)} 轮 · ${escapeHtml(event.phase || '-')}</span></div><em>#${Number(event.sequenceNo || 0)}</em></article>`;
  }).join('');
}

function renderServerCommentary(projection, gameId) {
  const session = getGameSession();
  const commentary = [...(projection?.commentary || [])].slice(-24).reverse();
  if (!commentary.length) return '<div class="real-empty">推进对局后，服务端会生成结构化赛事解说。</div>';
  const categoryIcons = { ELIMINATION: '☠', FINISH: '★', VOTE_RESULT: '⌁', ROUND_RESULT: '☀', PHASE: '↻', BATTLE_ACTION: '⚔', ZONE: '◉', SUPPLY_DROP: '✦', ZONE_DAMAGE: '☣', ACTION: '◌' };
  return commentary.map((item, index) => {
    const importance = Number(item.importance || 1) >= 5 ? 'critical' : Number(item.importance || 1) >= 3 ? 'key' : 'normal';
    const focused = item.highlightClipId && item.highlightClipId === session?.clipId;
    return `<article class="server-commentary-card importance-${importance} ${focused ? 'clip-focused' : ''} ${index === 0 ? 'latest' : ''}" ${item.highlightClipId ? `data-highlight-id="${escapeHtml(item.highlightClipId)}"` : ''}>
      <i>${escapeHtml(categoryIcons[item.category] || '◌')}</i><div><small>第 ${Number(item.roundNo || 0)} 轮 · ${escapeHtml(item.phase || '-')}</small><b>${escapeHtml(item.title)}</b><p>${escapeHtml(item.narration)}</p>${item.decisionSummary ? `<em>策略摘要 · ${escapeHtml(item.decisionSummary)}</em>` : ''}</div>
      <aside><span>#${Number(item.endSequenceNo || 0)}</span>${item.highlightClipId ? `<button class="text-button" type="button" data-share-clip="${escapeHtml(item.highlightClipId)}" data-share-game="${Number(gameId)}">分享片段</button>` : ''}</aside>
    </article>`;
  }).join('');
}

function playerRelatedEvents(events, playerId) {
  return events.filter((event) => samePlayerId(event.actorPlayerId, playerId)
    || samePlayerId(event.payload?.playerId, playerId)
    || samePlayerId(event.payload?.targetPlayerId, playerId)
    || samePlayerId(event.payload?.betrayerPlayerId, playerId)
    || samePlayerId(event.payload?.victimPlayerId, playerId)
    || samePlayerId(event.payload?.rescuerPlayerId, playerId)
    || samePlayerId(event.payload?.executorPlayerId, playerId)
    || (event.payload?.eliminatedIds || []).some((id) => samePlayerId(id, playerId)));
}

function renderPlayerDetail(player, game, events, publicView) {
  if (!player) return `<section class="arena-player-detail empty"><span>选择一名玩家</span><p>查看模型标签、公开行动，以及当前视角允许读取的角色或生存状态。</p></section>`;
  const allRelated = playerRelatedEvents(events, player.id);
  const related = allRelated.slice(-8).reverse();
  const isBattle = game.mode === 'BATTLE_ROYALE';
  const speeches = allRelated.filter((event) => ['PLAYER_SPOKE', 'PLAYER_FINAL_STATEMENT'].includes(event.eventType)).slice(-6).reverse();
  const interactions = allRelated.filter((event) => ['PLAYER_QUESTIONED', 'PLAYER_RESPONDED'].includes(event.eventType)).slice(-6).reverse();
  const votes = allRelated.filter((event) => event.eventType === 'PLAYER_VOTED').slice(-6).reverse();
  const skills = allRelated.filter((event) => ['WOLF_TARGET_SELECTED', 'GUARD_PROTECTED', 'SEER_CHECKED', 'WITCH_ACTION_COMPLETED'].includes(event.eventType)).slice(-6).reverse();
  const role = werewolfRoleLabel(player.roleCode);
  const persona = player.agentProfileName ? `<div class="player-persona"><span style="--agent-color:${escapeHtml(player.agentAccentColor || '#8B7CFF')}">${escapeHtml(player.agentAvatarEmoji || 'AI')}</span><div><b>${escapeHtml(player.agentProfileName)}</b><small>${escapeHtml((player.agentTraits || []).join(' · ') || player.agentDescription || '轻量人格')}</small><em>${escapeHtml(player.agentSpeakingStyle || '自然表达')} · ${escapeHtml(riskPreferenceLabel(player.agentRiskPreference))}</em><p>${escapeHtml(player.agentDescription || '')}${player.agentReasoningPreference ? ` · 推理重点：${escapeHtml(player.agentReasoningPreference)}` : ''}</p></div></div>` : '<div class="player-persona neutral"><span>AI</span><div><b>座位默认人格</b><small>按座位使用稳定的轻量人格</small></div></div>';
  const situation = currentMatchProjection?.werewolfSituation;
  const stance = situation?.players?.find((item) => Number(item.seatNo) === Number(player.seatNo));
  const claims = stance?.claims?.length ? stance.claims.map(werewolfRoleLabel).join('、') : '未公开声明';
  const socialState = isBattle ? '' : `<div class="player-public-stance"><h4>当前公开立场</h4><p><b>身份声明</b>${escapeHtml(claims)}</p><p><b>怀疑对象</b>${escapeHtml(werewolfRefText(stance?.suspects || []))}</p><p><b>支持对象</b>${escapeHtml(werewolfRefText(stance?.supports || []))}</p><p><b>投票意向</b>${stance?.voteIntent ? `${Number(stance.voteIntent.seatNo)}号 ${escapeHtml(stance.voteIntent.displayName || '')}` : '暂无'}</p>${stance?.reasonTags?.length ? `<small>${stance.reasonTags.map((tag) => escapeHtml(tag)).join(' · ')}</small>` : ''}</div>`;
  const audience = werewolfAudienceState(game.id);
  const canPredict = game.status !== 'FINISHED' && game.currentPhase !== 'DAY_VOTE';
  const audienceActions = isBattle ? '' : `<div class="player-audience-actions"><button type="button" class="${audience.suspectedWolfSeats.includes(Number(player.seatNo)) ? 'active suspect' : ''}" data-audience-action="suspect" data-seat-no="${Number(player.seatNo)}" ${game.status === 'FINISHED' ? 'disabled' : ''}>? 我怀疑</button><button type="button" class="${audience.followedSeats.includes(Number(player.seatNo)) ? 'active follow' : ''}" data-audience-action="follow" data-seat-no="${Number(player.seatNo)}">★ 关注</button><button type="button" class="${audience.eliminationPredictionSeatNo === Number(player.seatNo) ? 'active predict' : ''}" data-audience-action="predict" data-seat-no="${Number(player.seatNo)}" ${canPredict ? '' : 'disabled'}>票 预测本轮出局</button><small>个人预测只保存在此浏览器，不影响对局。</small></div>`;
  const body = isBattle
    ? `<div class="player-detail-stats"><span>状态<b>${player.status === 'DOWNED' ? '倒地' : player.status === 'ALIVE' ? '存活' : '淘汰'}</b></span><span>生命<b>${player.health ?? '-'}</b></span><span>护甲<b>${player.armor ?? 0}</b></span><span>补给<b>${player.food ?? '-'} / ${player.water ?? '-'}</b></span><span>淘汰<b>${player.kills ?? 0}</b></span></div><p>${player.region ? `当前位于 ${escapeHtml(player.region)} 区` : '当前位置在当前观战视角中不可见'} · ${escapeHtml(player.weaponName || '徒手')} ${player.hidden ? '· 隐蔽中' : ''}</p>`
    : `<div class="player-detail-stats"><span>状态<b>${player.status === 'ALIVE' ? '存活' : '出局'}</b></span><span>身份<b>${escapeHtml(role)}</b></span><span>发言<b>${speeches.length}</b></span><span>投票<b>${votes.length}</b></span></div>${player.roleCode === 'WITCH' ? `<p>解药 ${player.antidoteAvailable ? '可用' : '已使用'} · 毒药 ${player.poisonAvailable ? '可用' : '已使用'}</p>` : ''}${publicView && !player.roleCode ? '<p>身份与私有技能按公开视角隐藏。</p>' : ''}${socialState}${audienceActions}`;
  const eventRows = (items, emptyCopy) => items.length ? items.map((event) => {
    const info = describeGameEvent({ primary: event, decision: null }, window.currentRealPlayers || []);
    return `<article><i>${escapeHtml(info[0])}</i><span><b>${escapeHtml(info[1])}</b><small>第 ${Number(event.roundNo || 0)} 轮 · ${escapeHtml(info[2])}</small></span></article>`;
  }).join('') : `<p>${escapeHtml(emptyCopy)}</p>`;
  const history = isBattle
    ? `<h4>最近行动</h4>${eventRows(related, '暂无可见行动。')}`
    : `<h4>历史发言</h4>${eventRows(speeches, '暂无发言。')}<h4>质询与回应</h4>${eventRows(interactions, '暂无质询或回应。')}<h4>历史投票</h4>${eventRows(votes, '暂无投票。')}${!publicView || game.status === 'FINISHED' ? `<h4>技能行动</h4>${eventRows(skills, '暂无技能行动。')}` : ''}`;
  return `<section class="arena-player-detail"><header><span class="player-avatar avatar-${Number(player.seatNo || 0) % 5}">${escapeHtml(player.agentAvatarEmoji || String(player.displayName || '?').slice(0, 1))}</span><div><small>${Number(player.seatNo)} 号席位</small><h3>${escapeHtml(player.displayName)}</h3><p>${escapeHtml(modelDisplayLabel(player))}</p></div><button type="button" data-close-player aria-label="关闭玩家详情">×</button></header>${persona}${body}<div class="player-history">${history}</div></section>`;
}

function renderRegionSituation(players, eventGroups) {
  const latestZone = [...eventGroups].reverse().map((group) => group.primary).find((event) => event?.eventType === 'BATTLE_ZONE_UPDATED');
  const safeRegions = latestZone?.payload?.safeRegions || ['A', 'B', 'C', 'D', 'E'];
  const recentEvents = eventGroups.slice(-30).map((group) => group.primary).filter(Boolean);
  const supplyState = [...recentEvents].reverse().find((event) => ['BATTLE_SUPPLY_DROP', 'BATTLE_SUPPLY_CONTESTED', 'BATTLE_SUPPLY_CAPTURED'].includes(event.eventType));
  const supply = supplyState?.eventType === 'BATTLE_SUPPLY_CAPTURED' ? null : supplyState?.payload?.region;
  return `<div class="region-situation"><header><div><small>TACTICAL REGIONS</small><h3>战术区域态势</h3></div><span>离散区域 · 不表示真实距离</span></header><div>${['A', 'B', 'C', 'D', 'E'].map((region) => {
    const visible = players.filter((player) => player.region === region && player.status === 'ALIVE');
    const averageHealth = visible.length ? Math.round(visible.reduce((sum, player) => sum + Number(player.health || 0), 0) / visible.length) : 0;
    const attacks = recentEvents.filter((event) => event.eventType === 'BATTLE_ACTION_EXECUTED' && event.payload?.region === region && event.payload?.action === 'ATTACK').length;
    const searches = recentEvents.filter((event) => event.eventType === 'BATTLE_ACTION_EXECUTED' && event.payload?.region === region && event.payload?.action === 'SEARCH').length;
    const eliminations = recentEvents.filter((event) => event.eventType === 'BATTLE_PLAYER_ELIMINATED'
      && (event.payload?.region === region || findArenaPlayer(players, event.payload?.playerId)?.region === region)).length;
    const danger = !safeRegions.includes(region) ? '毒圈危险' : attacks >= 2 ? '交战热点' : supply === region ? '空投热点' : '态势平稳';
    return `<article class="${safeRegions.includes(region) ? 'safe' : 'storm'} ${supply === region ? 'supply' : ''}"><b>${region} 区 · ${escapeHtml(danger)}</b><span>${visible.length} 人可见 · 平均生命 ${averageHealth || '-'}</span><small>攻击 ${attacks} · 搜索 ${searches} · 淘汰 ${eliminations} · ${visible.filter((player) => player.armed).length} 人持械</small></article>`;
  }).join('')}</div></div>`;
}

function renderArenaTimeline(groups, players) {
  const nodes = groups.slice(-24);
  return `<nav class="arena-timeline" aria-label="最近行动时间轴"><span>较早</span><div>${nodes.map((group) => {
    const event = group.primary || group.decision;
    const info = describeGameEvent(group, players);
    const time = event?.createTime ? String(event.createTime).replace('T', ' ').slice(11, 19) : '';
    return `<button type="button" class="importance-${eventImportance(group)} ${samePlayerId(selectedTimelineSequence, event?.sequenceNo) ? 'selected' : ''}" data-timeline-sequence="${Number(event?.sequenceNo || 0)}" title="${escapeHtml(`${info[1]}${time ? ` · ${time}` : ''}`)}"><i></i><small>${Number(event?.roundNo || 0)}</small></button>`;
  }).join('') || '<em>推进后生成时间轴</em>'}</div><span>现在</span></nav>`;
}

function renderTimelineSelection(groups, players) {
  if (selectedTimelineSequence == null) return '';
  const group = groups.find((item) => samePlayerId((item.primary || item.decision)?.sequenceNo, selectedTimelineSequence));
  if (!group) return '';
  const event = group.primary || group.decision;
  const info = describeGameEvent(group, players);
  const time = event.createTime ? String(event.createTime).replace('T', ' ').slice(0, 19) : '时间未记录';
  return `<section class="timeline-event-detail"><i>${escapeHtml(info[0])}</i><div><small>第 ${Number(event.roundNo || 0)} 轮 · ${escapeHtml(event.phase || '-')} · #${Number(event.sequenceNo || 0)} · ${escapeHtml(time)}</small><b>${escapeHtml(info[1])}</b><p>${escapeHtml(info[2])}</p></div><button type="button" data-close-timeline>×</button></section>`;
}

function renderEventMoment(group, players) {
  if (!group || eventImportance(group) === 'normal') return '';
  const event = group.primary || group.decision;
  const momentTypes = new Set(['DAWN_RESULT', 'PLAYER_ELIMINATED', 'VOTE_RESULT', 'VOTE_TIED', 'GAME_FINISHED',
    'BATTLE_ZONE_UPDATED', 'BATTLE_SUPPLY_DROP', 'BATTLE_PLAYER_ELIMINATED', 'BATTLE_FINISHED']);
  if (!group.phaseChangedTo && !momentTypes.has(event?.eventType)
      && !(event?.eventType === 'BATTLE_ACTION_EXECUTED' && (event.payload?.critical || Number(event.payload?.damage || 0) >= 45))) return '';
  const info = describeGameEvent(group, players);
  const phaseCopy = group.phaseChangedTo === 'NIGHT_WOLF' ? ['☾', '夜晚开始', '公开视角将隐藏夜间秘密行动']
    : group.phaseChangedTo === 'DAY_DISCUSSION' ? ['☀', '天亮', '进入公开发言与推理阶段'] : null;
  const presentation = phaseCopy || info;
  return `<div class="event-moment importance-${eventImportance(group)}" aria-live="polite"><i>${escapeHtml(presentation[0])}</i><b>${escapeHtml(presentation[1])}</b><span>${escapeHtml(presentation[2])}</span></div>`;
}

function finishedPresentation(game, groups, players) {
  if (game.status !== 'FINISHED') return '';
  const finish = [...groups].reverse().map((group) => group.primary || group.decision)
    .find((event) => ['GAME_FINISHED', 'BATTLE_FINISHED'].includes(event?.eventType));
  const winnerId = finish?.payload?.winnerPlayerId;
  const winner = finish?.payload?.winnerName || findArenaPlayer(players, winnerId)?.displayName || finish?.payload?.winner || '胜负已结算';
  const eliminations = groups.filter((group) => ['PLAYER_ELIMINATED', 'BATTLE_PLAYER_ELIMINATED'].includes((group.primary || group.decision)?.eventType)).length;
  const battleStats = game.mode === 'BATTLE_ROYALE' && finish ? `<span>冠军淘汰 <b>${Number(finish.payload?.kills || 0)}</b></span><span>生存评分 <b>${Number(finish.payload?.score || 0)}</b></span>` : '';
  const battleReason = { LAST_SURVIVOR: '成为最后未淘汰者', MAX_ROUNDS_SCORE: '达到回合上限，按生存积分获胜', ALL_DOWNED_SCORE: '全员倒地，按生存积分结算' }[finish?.payload?.reason] || '成为最终幸存者';
  const scoreNames = { health: '生命', armor: '护甲', supplies: '补给', equipment: '装备', kills: '击杀', damage: '伤害' };
  const scoreDetails = game.mode === 'BATTLE_ROYALE' && finish?.payload?.scoreBreakdown
    ? `<p class="battle-score-details">${Object.entries(finish.payload.scoreBreakdown).map(([key, points]) => `${escapeHtml(scoreNames[key] || key)} ${Number(points)}分`).join(' · ')}</p>` : '';
  const audience = werewolfAudienceState(game.id);
  const actualWolves = players.filter((player) => player.roleCode === 'WEREWOLF').map((player) => Number(player.seatNo)).sort((a, b) => a - b);
  const predictedWolves = [...audience.suspectedWolfSeats].sort((a, b) => a - b);
  const wolfPrediction = game.mode === 'WEREWOLF' && predictedWolves.length
    ? (JSON.stringify(actualWolves) === JSON.stringify(predictedWolves) ? '狼人预测正确' : `狼人预测未命中 · 实际为 ${actualWolves.map((seat) => `${seat}号`).join('、')}`) : null;
  const lastVoteOut = [...groups].reverse().map((group) => group.primary).find((event) => event?.eventType === 'PLAYER_ELIMINATED'
    && event.payload?.reason === 'VOTE' && (!audience.predictionRound || Number(event.roundNo) === Number(audience.predictionRound)));
  const eliminationCorrect = audience.eliminationPredictionSeatNo && lastVoteOut
    ? Number(playerBySeatNo(players, audience.eliminationPredictionSeatNo)?.id) === Number(lastVoteOut.payload?.playerId) : null;
  const audienceResult = game.mode === 'WEREWOLF' && (wolfPrediction || audience.eliminationPredictionSeatNo)
    ? `<div class="audience-result"><b>${escapeHtml(wolfPrediction || '未提交狼人预测')}</b><span>${audience.eliminationPredictionSeatNo ? `${eliminationCorrect ? '本轮放逐预测正确' : '本轮放逐预测未命中'} · 你选择了${Number(audience.eliminationPredictionSeatNo)}号` : '未提交本轮放逐预测'}</span></div>` : '';
  return `<div class="match-finish-overlay"><span>最终结算</span><h2>${escapeHtml(winner)}</h2><p>${game.mode === 'BATTLE_ROYALE' ? battleReason : '赢得本场阵营对局'}</p><div class="finish-stats"><span>结束轮次 <b>${Number(game.currentRound || 0)}</b></span><span>淘汰事件 <b>${eliminations}</b></span>${battleStats}</div>${scoreDetails}${audienceResult}<footer><button class="primary" type="button" data-open-replay>查看完整复盘</button><button class="secondary" type="button" data-finish-home>返回竞技大厅</button></footer></div>`;
}

function playerBySeatNo(players, seatNo) {
  return players.find((player) => Number(player.seatNo) === Number(seatNo));
}

function latestTuringEvent(events, eventType, roundNo = null) {
  return [...events].reverse().find((event) => event.eventType === eventType
    && (roundNo == null || Number(event.roundNo) === Number(roundNo))) || null;
}

function turingSeatDisplay(players, seatNo) {
  const player = playerBySeatNo(players, seatNo);
  return seatNo == null ? '未知席位' : `${Number(seatNo)}号${player?.displayName ? ` · ${player.displayName}` : ''}`;
}

function turingSeatOptions(players, allowedSeatNos, selfSeatNo, placeholder = '请选择席位') {
  const allowed = allowedSeatNos?.length ? new Set(allowedSeatNos.map(Number)) : null;
  const options = [...players]
    .filter((player) => player.status === 'ALIVE' && Number(player.seatNo) !== Number(selfSeatNo)
      && (!allowed || allowed.has(Number(player.seatNo))))
    .sort((left, right) => Number(left.seatNo) - Number(right.seatNo));
  return `<option value="">${escapeHtml(placeholder)}</option>${options.map((player) => `<option value="${Number(player.seatNo)}">${Number(player.seatNo)}号 · ${escapeHtml(player.displayName)}</option>`).join('')}`;
}

const TURING_TEXT_RULES = Object.freeze({
  ANSWER: { field: 'answer', label: '回答', min: 30, max: 90, sentences: 2 },
  SUSPECT: { field: 'reasonSummary', label: '怀疑理由', min: 15, max: 50, sentences: 2 },
  QUESTION: { field: 'question', label: '质询', min: 12, max: 45, sentences: 1 },
  RESPOND: { field: 'response', label: '回应', min: 20, max: 70, sentences: 2 },
  DEFEND: { field: 'defense', label: '辩护', min: 25, max: 80, sentences: 2 },
  VOTE: { field: 'reasonSummary', label: '投票理由', min: 12, max: 40, sentences: 1 }
});

const TURING_STAGES = Object.freeze([
  ['公开题目', 'OPEN PROMPT'], ['匿名回答', 'ANSWER REVEAL'], ['秘密怀疑', 'SUSPICION'],
  ['质询回应', 'CROSS EXAM'], ['公开辩护', 'DEFENSE'], ['秘密投票', 'BALLOT'], ['身份结算', 'RESULT']
]);

function turingStageIndex(phase) {
  if (['WAITING', 'ROUND_PROMPT', 'ANSWER_COLLECTION'].includes(phase)) return 0;
  if (phase === 'ANSWER_REVEAL') return 1;
  if (['SUSPICION_COLLECTION', 'CONTROVERSY_REVEAL'].includes(phase)) return 2;
  if (['QUESTION', 'RESPONSE'].includes(phase)) return 3;
  if (phase === 'DEFENSE') return 4;
  if (['VOTE_COLLECTION', 'VOTE_REVEAL'].includes(phase)) return 5;
  return 6;
}

function turingTextLength(value) {
  return Array.from(String(value || '')).length;
}

function normalizeTuringPublicText(rawValue) {
  return String(rawValue || '').replace(/[\u0000\uFEFF]/gu, '').trim()
    .replace(/^```(?:json|markdown|text)?\s*/iu, '').replace(/\s*```$/u, '')
    .replace(/^\s{0,3}#{1,6}\s*/gmu, '')
    .replace(/^\s*(?:[-*+]\s+|\d{1,2}[.)、]\s*)/gmu, '')
    .replace(/^(?:答案|回答|我的回答(?:是)?|答复|结论|理由|辩护|质询|回应)\s*[：:]\s*/iu, '')
    .replace(/\r?\n+/gu, '；').replace(/,/gu, '，').replace(/:/gu, '：').replace(/;/gu, '；')
    .replace(/\?/gu, '？').replace(/!/gu, '！').replace(/[ \t\f]+/gu, ' ')
    .replace(/\s*([，。：；！？])\s*/gu, '$1')
    .replace(/，{2,}/gu, '，').replace(/。{2,}/gu, '。').replace(/：{2,}/gu, '：')
    .replace(/；{2,}/gu, '；').replace(/！{2,}/gu, '！').replace(/？{2,}/gu, '？').trim();
}

function validateTuringText(action, rawValue) {
  const rule = TURING_TEXT_RULES[action];
  if (!rule) return { valid: true, message: '', count: 0 };
  const value = normalizeTuringPublicText(rawValue);
  const count = turingTextLength(value);
  if (count < rule.min) return { valid: false, message: `${rule.label}还需 ${rule.min - count} 字`, count };
  if (count > rule.max) return { valid: false, message: `${rule.label}最多 ${rule.max} 字，当前 ${count} 字`, count };
  const sentences = value.split(/[。！？；!?;]+/u).filter((part) => part.trim()).length;
  if (sentences > rule.sentences) return { valid: false, message: `${rule.label}最多 ${rule.sentences} 句`, count };
  const evidence = /公开|回答|解释|理由|质询|回应|立场|选择|判断|前后|变化|记录|信息|行为|表述|投票|跟随|反驳|取舍/u;
  const reason = /因为|由于|所以|否则|理由|来自|在于|能|可以|需要|避免|让|但|而|却|若|如果|依据/u;
  if (/我是\s*(?:AI|人工智能|真人)|OpenAI|ChatGPT|Claude|Gemini|DeepSeek|Qwen|系统提示词|API\s*Key|Base\s*URL/iu.test(value)) {
    return { valid: false, message: '请只讨论公开游戏内容，不要提及身份、模型或系统信息', count };
  }
  if (action === 'ANSWER' && (!/我|选择|接受|保留|删除|愿意|倾向|支持|反对|应该|会|不会|判断|主张/u.test(value) || !reason.test(value))) {
    return { valid: false, message: '请明确写出选择或立场，并补充具体理由', count };
  }
  if (action === 'SUSPECT' && (!evidence.test(value) && !reason.test(value))) {
    return { valid: false, message: '请引用公开回答、立场变化或质询表现', count };
  }
  if (action === 'SUSPECT' && /像(?:AI|人工智能|真人)|感觉是他|凭感觉/iu.test(value)) {
    return { valid: false, message: '不能用“像 AI / 真人”或单纯感觉作为怀疑理由', count };
  }
  const questionForm = /为什么|为何|如何|哪|什么|是否|能否|请.{0,12}(?:解释|说明|回答|指出)/u;
  if (action === 'QUESTION' && ((value.match(/[？?]/gu) || []).length > 1 || !evidence.test(value) || !questionForm.test(value))) {
    return { valid: false, message: '一次只问一个围绕公开内容的核心问题', count };
  }
  const compact = value.replace(/[\s，。：；！？、,.!?;:'"“”‘’]/gu, '');
  if (action === 'RESPOND' && /^(?:(?:不知道|不清楚|不是|你猜)){1,}$/u.test(compact)) {
    return { valid: false, message: '请直接回答质询，并说明判断依据', count };
  }
  if (action === 'DEFEND' && !evidence.test(value) && !reason.test(value)) {
    return { valid: false, message: '请解释你为何被怀疑，并回应公开依据', count };
  }
  if (action === 'VOTE' && !evidence.test(value)) {
    return { valid: false, message: '请用公开回答、质询或立场表现说明投票理由', count };
  }
  return { valid: true, message: '', count };
}

function renderTuringTextField(action, label, placeholder) {
  const rule = TURING_TEXT_RULES[action];
  return `<label class="turing-text-field"><span>${escapeHtml(label)}</span><textarea name="${rule.field}" maxlength="${rule.max}" rows="3" required data-turing-text-input placeholder="${escapeHtml(placeholder)}"></textarea><footer><small data-turing-field-error>${rule.label}还需 ${rule.min} 字</small><b data-turing-counter>0 / ${rule.min}–${rule.max}</b></footer></label>`;
}

function renderTuringHumanAction(game, players, publicView) {
  const completed = Number(game.submissionCompletedCount || 0);
  const required = Number(game.submissionRequiredCount || 0);
  if (publicView) return `<div class="turing-readonly"><b>公开只读观战</b><p>当前阶段匿名进度 ${completed} / ${required || '—'}，秘密内容只在统一揭示节点公开。</p></div>`;
  if (game.status !== 'RUNNING') return '';
  if (!game.humanActionRequired) {
    return `<div class="turing-waiting-human"><i></i><b>等待本阶段提交</b><p>匿名进度 ${completed} / ${required || '—'}；界面不会展示提交者、顺序或处理状态。</p></div>`;
  }
  const action = game.requiredHumanAction;
  const selfSeatNo = Number(game.selfSeatNo);
  const allOptions = turingSeatOptions(players, null, selfSeatNo);
  const revoteOptions = turingSeatOptions(players, game.revoteCandidateSeatNos, selfSeatNo, '选择平票候选人');
  const target = action === 'QUESTION' ? game.currentQuestionTargetSeatNo : game.currentQuestionerSeatNo;
  const title = ({ ANSWER: '提交你的匿名回答', SUSPECT: '锁定你的怀疑判断', QUESTION: '向争议席位提问', RESPOND: '回应当前质询', DEFEND: '完成公开辩护', VOTE: game.revoteCandidateSeatNos?.length ? '提交秘密重投' : '提交秘密投票' })[action] || '完成你的行动';
  let fields = '';
  if (action === 'ANSWER') {
    fields = renderTuringTextField('ANSWER', '你的匿名回答', '明确选择或立场，并用一条具体理由支撑');
  } else if (action === 'SUSPECT') {
    fields = `<div class="turing-form-grid"><label>首要怀疑对象<select name="primarySuspectSeatNo" required>${allOptions}</select></label><label>第二怀疑对象（可选）<select name="secondarySuspectSeatNo">${allOptions}</select></label></div><fieldset><legend>公开内容标签（最多 3 个）</legend><label><input type="checkbox" name="reasonTags" value="POSITION_SHIFT"> 立场变化</label><label><input type="checkbox" name="reasonTags" value="EVIDENCE_GAP"> 公开依据不足</label><label><input type="checkbox" name="reasonTags" value="EVASIVE"> 回避质询</label><label><input type="checkbox" name="reasonTags" value="FOLLOWING"> 跟随他人</label></fieldset>${renderTuringTextField('SUSPECT', '怀疑理由', '引用一条公开回答、立场变化或质询表现')}`;
  } else if (action === 'QUESTION') {
    fields = `<div class="turing-fixed-target"><span>质询对象</span><b>${escapeHtml(turingSeatDisplay(players, target))}</b></div><input type="hidden" name="targetSeatNo" value="${Number(target)}">${renderTuringTextField('QUESTION', '你的问题', '只问一个围绕对方公开内容的核心问题')}`;
  } else if (action === 'RESPOND') {
    fields = `<div class="turing-fixed-target"><span>回应对象</span><b>${escapeHtml(turingSeatDisplay(players, target))}</b></div><input type="hidden" name="targetSeatNo" value="${Number(target)}">${renderTuringTextField('RESPOND', '你的回应', '直接回答问题，并说明判断依据')}`;
  } else if (action === 'DEFEND') {
    fields = `${renderTuringTextField('DEFEND', '公开辩护', '解释为何被怀疑，并回应一条公开依据')}<label>反向怀疑对象（可选）<select name="counterSuspectSeatNo">${allOptions}</select></label>`;
  } else if (action === 'VOTE') {
    fields = `<label>${game.revoteCandidateSeatNos?.length ? '重投对象' : '淘汰对象'}<select name="targetSeatNo" required>${game.revoteCandidateSeatNos?.length ? revoteOptions : allOptions}</select></label>${renderTuringTextField('VOTE', '投票理由', '用一条公开回答、质询或立场表现说明理由')}`;
  }
  return `<form id="turing-human-form" class="turing-human-form" data-turing-human-form data-action="${escapeHtml(action || '')}"><header><span>YOUR TURN · ${Number(game.selfSeatNo)}号位</span><h3>${escapeHtml(title)}</h3><p>提交后不可修改；内容只会在本阶段规定的统一揭示节点公开。</p></header><div class="turing-form-body">${fields}</div><button class="primary turing-form-submit" type="submit">锁定并提交</button></form>`;
}

function renderTuringFinish(game, players) {
  if (game.status !== 'FINISHED') return '';
  const recap = game.turingRecap || {};
  const winner = game.winner || recap.winner;
  const eliminationOrder = recap.eliminationOrder || [];
  const roundSummaries = recap.roundSummaries || [];
  const humanSeat = recap.humanSeatNo || players.find((player) => player.playerType === 'HUMAN' || player.roleCode === 'HUMAN')?.seatNo;
  const playerModels = [...players].sort((left, right) => Number(left.seatNo) - Number(right.seatNo)).map((player) => {
    const human = player.playerType === 'HUMAN' || player.roleCode === 'HUMAN';
    const modelLabel = human ? '真人玩家' : player.localStrategy ? '系统策略' : modelDisplayLabel(player);
    return `<article><i>${Number(player.seatNo)}</i><div><b>${escapeHtml(player.displayName)}</b><span>${escapeHtml(modelLabel)}</span><small>${escapeHtml(player.agentProfileName || (human ? '现场表达' : '座位默认人格'))}</small></div><em>${human ? 'HUMAN' : 'AI'}</em></article>`;
  }).join('');
  const roundHistory = roundSummaries.map((round) => {
    const answers = (round.answers || []).map((answer) => `<span class="turing-round-answer"><i>${Number(answer.seatNo)}号</i>${escapeHtml(answer.answer || '未记录回答')}</span>`).join('');
    const suspicionTotals = (round.suspicions || []).reduce((totals, item) => {
      const seatNo = Number(item.primarySuspectSeatNo);
      if (seatNo) totals[seatNo] = Number(totals[seatNo] || 0) + 1;
      return totals;
    }, {});
    const suspicionRanking = Object.entries(suspicionTotals)
      .sort((left, right) => Number(right[1]) - Number(left[1]) || Number(left[0]) - Number(right[0]))
      .map(([seatNo, count]) => `${seatNo}号 ${count}次`).join(' · ');
    const suspicionLinks = (round.suspicions || []).map((item) => `${Number(item.actorSeatNo)}号 → ${Number(item.primarySuspectSeatNo)}号`).join(' · ');
    const voteLinks = (round.voteRounds || []).map((voteRound, index) => `<span>${index ? '重投' : '首投'}：${voteRound.map((vote) => `${Number(vote.voterSeatNo)}→${Number(vote.targetSeatNo)}`).join(' · ') || '无'}</span>`).join('');
    return `<article><header><b>第 ${Number(round.roundNo)} 轮</b><em>${round.eliminatedSeatNo ? `${Number(round.eliminatedSeatNo)}号出局 · ${round.eliminatedIdentity === 'HUMAN' ? '真人' : 'AI'}` : '无人出局'}</em></header><p>${escapeHtml(round.question || '')}</p><section class="turing-round-answers"><b>匿名回答</b><div>${answers || '<span>暂无回答记录</span>'}</div></section><small>怀疑排行：${escapeHtml(suspicionRanking || '无')}</small><small>首要怀疑关系：${escapeHtml(suspicionLinks || '无')}</small><div class="turing-round-votes">${voteLinks || '<span>无有效投票</span>'}</div></article>`;
  }).join('');
  return `<section class="turing-finish"><header><span>IDENTITIES REVEALED</span><h2>${winner === 'HUMAN' ? '真人成功潜伏' : 'AI 成功识破真人'}</h2><strong>真人是：${humanSeat ? `${Number(humanSeat)}号 · ${escapeHtml(playerBySeatNo(players, humanSeat)?.displayName || '')}` : '未记录'}</strong><p>${escapeHtml(turingFinishedReasonLabel(game.finishedReason || recap.finishedReason))}</p></header><div class="turing-finish-stats"><span>完成轮次<b>${Number(roundSummaries.length || game.currentRound || 0)}</b></span><span>误淘汰 AI<b>${Number(recap.wrongAiEliminationCount ?? game.wrongAiEliminationCount ?? 0)}</b></span><span>淘汰顺序<b>${eliminationOrder.length ? eliminationOrder.map((seat) => `${seat}号`).join(' → ') : '无人'}</b></span><span>真人存活轮次<b>${Number(recap.humanSurvivedRounds || 0)}</b></span></div><div class="turing-recap-insights"><span>最先识破真人 <b>${recap.firstCorrectSuspectSeatNo ? `${recap.firstCorrectSuspectSeatNo}号` : '无人'}</b></span><span>判断最准确 AI <b>${recap.mostAccurateAiSeatNo ? `${recap.mostAccurateAiSeatNo}号` : '未产生'}</b></span><span>受误导最深 AI <b>${recap.mostMisledAiSeatNo ? `${recap.mostMisledAiSeatNo}号` : '未产生'}</b></span></div><div class="turing-round-recap"><h3>逐轮怀疑与投票</h3>${roundHistory || '<p>暂无轮次明细。</p>'}</div><div class="turing-model-reveal"><h3>身份与模型揭晓</h3>${playerModels}</div><footer><button class="primary" type="button" data-open-replay>查看完整复盘</button><button class="secondary" type="button" data-finish-home>返回竞技大厅</button></footer></section>`;
}

function renderTuringIdentityResult(game, players) {
  const recap = game.turingRecap || {};
  const winner = game.winner || recap.winner;
  const humanSeat = recap.humanSeatNo || players.find((player) => player.playerType === 'HUMAN' || player.roleCode === 'HUMAN')?.seatNo;
  const identities = [...players].sort((a, b) => Number(a.seatNo) - Number(b.seatNo)).map((player) => {
    const human = player.playerType === 'HUMAN' || player.roleCode === 'HUMAN';
    const modelLabel = human ? '真人玩家' : player.localStrategy ? '系统策略' : modelDisplayLabel(player);
    return `<article class="turing-result-seat"><i>${Number(player.seatNo)}</i><div><b>${escapeHtml(player.displayName)}</b><small>${escapeHtml(modelLabel)}</small><small>${escapeHtml(player.agentProfileName || '匿名人格')}</small></div><em>${human ? 'HUMAN' : 'AI'}</em></article>`;
  }).join('');
  const eliminationOrder = recap.eliminationOrder || [];
  return `<section class="turing-result-view"><header><span>IDENTITIES REVEALED</span><h2>${winner === 'HUMAN' ? '真人成功潜伏' : 'AI 成功识破真人'}</h2><p>${escapeHtml(turingFinishedReasonLabel(game.finishedReason || recap.finishedReason))}</p></header><div class="turing-result-hero"><strong>真人席位</strong><b>${humanSeat ? `${Number(humanSeat)}号` : '未记录'}</b><span>${humanSeat ? escapeHtml(playerBySeatNo(players, humanSeat)?.displayName || '') : ''}</span></div><div class="turing-result-grid">${identities}</div><footer><span>完成轮次 <b>${Number((recap.roundSummaries || []).length || game.currentRound || 0)}</b></span><span>误淘汰 AI <b>${Number(recap.wrongAiEliminationCount ?? game.wrongAiEliminationCount ?? 0)}</b></span><span>淘汰顺序 <b>${eliminationOrder.length ? eliminationOrder.map((seat) => `${Number(seat)}号`).join(' → ') : '无人'}</b></span><span>最先识破真人 <b>${recap.firstCorrectSuspectSeatNo ? `${Number(recap.firstCorrectSuspectSeatNo)}号` : '无人'}</b></span><span>判断最准确 AI <b>${recap.mostAccurateAiSeatNo ? `${Number(recap.mostAccurateAiSeatNo)}号` : '未产生'}</b></span><span>受误导最深 AI <b>${recap.mostMisledAiSeatNo ? `${Number(recap.mostMisledAiSeatNo)}号` : '未产生'}</b></span></footer></section>`;
}

function renderTuringAnswerCards(answers) {
  if (!answers.length) return '<div class="turing-stage-empty"><i>⌁</i><b>回答仍在密封</b><p>所有存活玩家完成提交后，六份正文会同时出现。</p></div>';
  return `<div class="turing-answer-grid">${answers.map((answer) => `<article class="turing-answer-card"><header><i>${Number(answer.seatNo)}</i><b>${escapeHtml(answer.displayName || `${answer.seatNo}号玩家`)}</b></header><p>${escapeHtml(answer.answer || '')}</p><button type="button" data-expand-answer>展开全文</button></article>`).join('')}</div>`;
}

function renderTuringSuspicionChart(game, players) {
  const counts = Object.entries(game.publicSuspicionCounts || {})
    .sort((a, b) => Number(b[1]) - Number(a[1]) || Number(a[0]) - Number(b[0]));
  const maximum = Math.max(1, ...counts.map(([, count]) => Number(count)));
  if (!counts.length) return '<div class="turing-stage-empty compact"><b>怀疑仍在密封</b><p>只显示匿名总进度，不公开个人选择。</p></div>';
  return `<div class="turing-bar-chart">${counts.map(([seatNo, count]) => `<article><span>${escapeHtml(turingSeatDisplay(players, seatNo))}</span><div><i style="--value:${Math.round(Number(count) / maximum * 100)}%"></i></div><b>${Number(count)}</b></article>`).join('')}</div><p class="turing-chart-note">争议席位：${(game.controversySeatNos || []).length ? game.controversySeatNos.map((seat) => `${Number(seat)}号`).join('、') : '等待统一揭示'}</p>`;
}

function renderTuringConversation(events, players, roundNo) {
  const items = events.filter((event) => Number(event.roundNo) === roundNo
    && ['TURING_QUESTION_ASKED', 'TURING_RESPONSE_GIVEN'].includes(event.eventType));
  if (!items.length) return '<div class="turing-stage-empty compact"><b>质询尚未开始</b><p>争议结果揭示后，这里只呈现公开问答。</p></div>';
  return `<div class="turing-conversation">${items.map((event) => {
    const question = event.eventType === 'TURING_QUESTION_ASKED';
    const from = question ? event.payload?.questionerSeatNo : event.payload?.responderSeatNo;
    const to = event.payload?.targetSeatNo;
    const copy = question ? event.payload?.question : event.payload?.response;
    return `<article><header><i>${question ? 'Q' : 'A'}</i><b>${Number(from)}号</b><span>对 ${Number(to)}号</span></header><p>${escapeHtml(copy || '')}</p></article>`;
  }).join('')}</div>`;
}

function renderTuringDefenseCards(events, roundNo) {
  const items = events.filter((event) => Number(event.roundNo) === roundNo && event.eventType === 'TURING_DEFENSE_GIVEN');
  if (!items.length) return '<div class="turing-stage-empty compact"><b>等待公开辩护</b><p>辩护正文会使用同一张卡片结构依次公开。</p></div>';
  return `<div class="turing-defense-grid">${items.map((event) => `<article><header><i>${Number(event.payload?.seatNo)}</i><b>${event.payload?.purpose === 'TIE_BREAK' ? '平票辩护' : '争议辩护'}</b></header><p>${escapeHtml(event.payload?.defense || '')}</p>${event.payload?.counterSuspectSeatNo ? `<small>反向怀疑 ${Number(event.payload.counterSuspectSeatNo)}号</small>` : '<small>未提出反向怀疑</small>'}</article>`).join('')}</div>`;
}

function renderTuringVoteReveal(voteReveal, players) {
  const votes = voteReveal?.payload?.votes || [];
  const counts = Object.entries(voteReveal?.payload?.voteCounts || {});
  if (!votes.length) return '<div class="turing-stage-empty compact"><b>选票仍在密封</b><p>不会提前展示任何席位的投票去向。</p></div>';
  const maximum = Math.max(1, ...counts.map(([, count]) => Number(count)));
  return `<div class="turing-vote-reveal"><section><h3>投票关系</h3><div>${votes.map((vote) => `<article><b>${Number(vote.voterSeatNo)}号</b><i>→</i><strong>${Number(vote.targetSeatNo)}号</strong><small>${escapeHtml(vote.reasonSummary || '公开表现')}</small></article>`).join('')}</div></section><section><h3>得票分布</h3><div class="turing-bar-chart">${counts.sort((a, b) => Number(b[1]) - Number(a[1])).map(([seatNo, count]) => `<article><span>${escapeHtml(turingSeatDisplay(players, seatNo))}</span><div><i style="--value:${Math.round(Number(count) / maximum * 100)}%"></i></div><b>${Number(count)}</b></article>`).join('')}</div></section></div>`;
}

function renderTuringStage(game, players, events, publicView) {
  const phase = game.currentPhase;
  const roundNo = Number(game.currentRound || 1);
  const answerReveal = latestTuringEvent(events, 'TURING_ANSWERS_REVEALED', roundNo);
  const voteReveal = latestTuringEvent(events, 'TURING_VOTES_REVEALED', roundNo);
  const action = renderTuringHumanAction(game, players, publicView);
  const heading = (eyebrow, title, copy) => `<header class="turing-stage-heading"><span>${escapeHtml(eyebrow)}</span><h2>${escapeHtml(title)}</h2><p>${escapeHtml(copy)}</p></header>`;
  if (game.status === 'FINISHED' || phase === 'FINISHED') return renderTuringIdentityResult(game, players);
  if (['WAITING', 'ROUND_PROMPT', 'ANSWER_COLLECTION'].includes(phase)) {
    return `<section class="turing-current-stage prompt-stage">${heading(`ROUND ${String(roundNo).padStart(2, '0')} · OPEN PROMPT`, game.currentQuestion || '本轮公开问题', '所有存活玩家回答同一道题，公开卡片不会携带身份样式。')}<div class="turing-prompt-body">${action || '<div class="turing-stage-empty compact"><b>等待房主启动</b><p>启动后会在这里完成第一份匿名回答。</p></div>'}</div></section>`;
  }
  if (phase === 'ANSWER_REVEAL') {
    return `<section class="turing-current-stage">${heading('SIMULTANEOUS REVEAL', '匿名回答统一揭示', '六张卡片使用完全相同的结构，只比较公开正文。')}${renderTuringAnswerCards(answerReveal?.payload?.answers || [])}</section>`;
  }
  if (phase === 'SUSPICION_COLLECTION') {
    return `<section class="turing-current-stage">${heading('SECRET SUSPICION', '锁定公开内容判断', '怀疑对象与理由会保持密封，只展示整体完成进度。')}<div class="turing-stage-action">${action}</div></section>`;
  }
  if (phase === 'CONTROVERSY_REVEAL') {
    return `<section class="turing-current-stage">${heading('AGGREGATED RESULT', '怀疑结果统一揭示', '这里只展示汇总计数，不展示提交顺序。')}${renderTuringSuspicionChart(game, players)}</section>`;
  }
  if (['QUESTION', 'RESPONSE'].includes(phase)) {
    return `<section class="turing-current-stage">${heading('CROSS EXAMINATION', '质询与回应', '一问一答围绕已经公开的内容展开。')}<div class="turing-stage-split">${renderTuringConversation(events, players, roundNo)}${action ? `<aside>${action}</aside>` : ''}</div></section>`;
  }
  if (phase === 'DEFENSE') {
    return `<section class="turing-current-stage">${heading('PUBLIC DEFENSE', '争议席位公开辩护', '每份辩护都使用相同结构，不显示生成过程。')}<div class="turing-stage-split">${renderTuringDefenseCards(events, roundNo)}${action ? `<aside>${action}</aside>` : ''}</div></section>`;
  }
  if (phase === 'VOTE_COLLECTION') {
    return `<section class="turing-current-stage">${heading('SEALED BALLOT', game.revoteCandidateSeatNos?.length ? '秘密重投' : '秘密投票', '投票去向在全部完成前保持密封。')}<div class="turing-stage-action">${action}</div></section>`;
  }
  if (phase === 'VOTE_REVEAL') {
    return `<section class="turing-current-stage">${heading('BALLOT REVEAL', voteReveal?.payload?.revote ? '重投结果' : '投票结果', '关系图与得票条形图在同一时刻公开。')}${renderTuringVoteReveal(voteReveal, players)}</section>`;
  }
  const elimination = latestTuringEvent(events, 'TURING_PLAYER_ELIMINATED', roundNo);
  return `<section class="turing-current-stage">${heading('ROUND RESULT', `第 ${roundNo} 轮结算`, '本轮公开阶段已经完成。')}<div class="turing-round-result"><i>${elimination?.payload?.seatNo ? Number(elimination.payload.seatNo) : '—'}</i><div><b>${elimination?.payload?.seatNo ? `${Number(elimination.payload.seatNo)}号出局` : '本轮无人出局'}</b><p>${elimination?.payload?.identity ? `已公开身份：${elimination.payload.identity === 'HUMAN' ? '真人' : 'AI'}` : '身份结算将在规则节点统一公开。'}</p></div></div></section>`;
}

function renderTuringSeatSidebar(game, players) {
  return `<section class="turing-seat-sidebar"><header><span>ANONYMOUS LINEUP</span><h2>六席玩家</h2><p>仅展示席位、人格与存活状态</p></header><div>${[...players].sort((a, b) => Number(a.seatNo) - Number(b.seatNo)).map((player) => `<article class="turing-seat-card" style="--seat-accent:${escapeHtml(player.agentAccentColor || '#d7ff43')}"><i>${escapeHtml(player.agentAvatarEmoji || String(player.seatNo))}</i><div><b>${Number(player.seatNo)}号 · ${escapeHtml(player.displayName)}</b><span>${escapeHtml(player.agentProfileName || '匿名人格')}</span><small>${escapeHtml((player.agentTraits || []).slice(0, 2).join(' · ') || player.agentSpeakingStyle || '公开表达')}</small></div><em>${player.status === 'ALIVE' ? '存活' : '出局'}</em></article>`).join('')}</div></section>`;
}

function renderTuringHistory(events, players) {
  const hiddenTypes = new Set(['TURING_PHASE_CHANGED', 'TURING_ANSWER_SUBMITTED', 'TURING_SUSPICION_SUBMITTED', 'TURING_VOTE_SUBMITTED']);
  const items = [...events].reverse().filter((event) => !hiddenTypes.has(event.eventType)).slice(0, 40);
  return `<aside class="turing-history-drawer ${turingHistoryOpen ? 'open' : ''}" aria-hidden="${turingHistoryOpen ? 'false' : 'true'}"><header><div><span>PUBLIC HISTORY</span><h2>已完成阶段记录</h2></div><button type="button" data-close-turing-history aria-label="关闭记录">×</button></header><div>${items.map((event) => {
    const info = describeGameEvent({ primary: event, decision: null }, players);
    return `<article><i>${escapeHtml(info[0])}</i><div><small>第 ${Number(event.roundNo || 0)} 轮</small><b>${escapeHtml(info[1])}</b><p>${escapeHtml(info[2])}</p></div></article>`;
  }).join('') || '<div class="turing-stage-empty compact"><p>尚无已公开阶段记录。</p></div>'}</div></aside><button class="turing-history-scrim ${turingHistoryOpen ? 'open' : ''}" type="button" data-close-turing-history aria-label="关闭记录遮罩"></button>`;
}

function updateTuringFormValidation(form) {
  if (!form) return true;
  const action = form.dataset.action;
  const rule = TURING_TEXT_RULES[action];
  if (!rule) return true;
  const field = form.elements[rule.field];
  const result = validateTuringText(action, field?.value || '');
  const counter = form.querySelector('[data-turing-counter]');
  const error = form.querySelector('[data-turing-field-error]');
  if (counter) counter.textContent = `${result.count} / ${rule.min}–${rule.max}`;
  if (error) { error.textContent = result.valid ? '格式符合公开文本规则' : result.message; error.classList.toggle('valid', result.valid); }
  field?.setAttribute('aria-invalid', result.valid ? 'false' : 'true');
  return result.valid;
}

async function submitTuringHumanAction(event) {
  event.preventDefault();
  const session = getGameSession();
  const form = event.currentTarget;
  if (!session || session.publicView) return;
  const button = form.querySelector('button[type="submit"]');
  const data = new FormData(form);
  const action = form.dataset.action;
  if (!updateTuringFormValidation(form)) {
    const message = form.querySelector('[data-turing-field-error]')?.textContent || '请检查公开文本格式';
    showToast(message);
    form.querySelector('[data-turing-text-input]')?.focus();
    return;
  }
  const value = (name) => normalizeTuringPublicText(data.get(name));
  const seat = (name) => value(name) ? Number(value(name)) : null;
  const payload = { action };
  if (action === 'ANSWER') payload.answer = value('answer');
  if (action === 'SUSPECT') {
    payload.primarySuspectSeatNo = seat('primarySuspectSeatNo');
    const second = seat('secondarySuspectSeatNo');
    if (second && second === payload.primarySuspectSeatNo) {
      showToast('第二怀疑对象不能与首要怀疑对象重复');
      return;
    }
    payload.suspectSeatNos = [...new Set([payload.primarySuspectSeatNo, second].filter(Boolean))];
    payload.reasonTags = data.getAll('reasonTags').slice(0, 3);
    payload.reasonSummary = value('reasonSummary') || null;
  }
  if (action === 'QUESTION') { payload.targetSeatNo = seat('targetSeatNo'); payload.question = value('question'); }
  if (action === 'RESPOND') { payload.targetSeatNo = seat('targetSeatNo'); payload.response = value('response'); }
  if (action === 'DEFEND') { payload.defense = value('defense'); payload.counterSuspectSeatNo = seat('counterSuspectSeatNo'); payload.reasonSummary = value('reasonSummary') || null; }
  if (action === 'VOTE') { payload.targetSeatNo = seat('targetSeatNo'); payload.reasonSummary = value('reasonSummary') || null; }
  button.disabled = true;
  button.textContent = '正在锁定…';
  try {
    await apiRequest(`/api/games/${session.gameId}/turing-human-actions`, { method: 'POST', body: JSON.stringify(payload) });
    showToast('你的行动已锁定');
    await loadCurrentGame(true);
  } catch (error) {
    showToast(`提交失败：${error.message}`);
    button.disabled = false;
    button.textContent = '锁定并提交';
  }
}

function renderLegacyTuringHuntWatch(game, players, events, publicView = false) {
  const dashboard = ensureWatchDashboard();
  if (!dashboard) return;
  const roundNo = Number(game.currentRound || 1);
  const answerReveal = latestTuringEvent(events, 'TURING_ANSWERS_REVEALED', roundNo);
  const voteReveal = latestTuringEvent(events, 'TURING_VOTES_REVEALED', roundNo);
  const answers = answerReveal?.payload?.answers || [];
  const votes = voteReveal?.payload?.votes || [];
  const questionEvents = events.filter((event) => Number(event.roundNo) === roundNo
    && ['TURING_QUESTION_ASKED', 'TURING_RESPONSE_GIVEN', 'TURING_DEFENSE_GIVEN'].includes(event.eventType));
  const identityBySeat = {};
  events.filter((event) => ['TURING_IDENTITY_REVEALED', 'TURING_PLAYER_ELIMINATED'].includes(event.eventType))
    .forEach((event) => { if (event.payload?.seatNo) identityBySeat[Number(event.payload.seatNo)] = event.payload.identity; });
  const suspicionCounts = game.publicSuspicionCounts || {};
  const voteCounts = game.publicVoteCounts || voteReveal?.payload?.voteCounts || {};
  const secretAnswerCount = events.filter((event) => event.eventType === 'TURING_ANSWER_SUBMITTED' && Number(event.roundNo) === roundNo).length;
  const secretVoteCount = events.filter((event) => event.eventType === 'TURING_VOTE_SUBMITTED' && Number(event.roundNo) === roundNo).length;
  const ranking = Object.entries(suspicionCounts).sort((left, right) => Number(right[1]) - Number(left[1]) || Number(left[0]) - Number(right[0]));
  const answerPanel = answers.length ? answers.map((answer) => `<article><header><i>${Number(answer.seatNo)}</i><b>${escapeHtml(answer.displayName || `${answer.seatNo}号玩家`)}</b></header><p>${escapeHtml(answer.answer)}</p>${answer.reasonSummary ? `<small>答题策略 · ${escapeHtml(answer.reasonSummary)}</small>` : ''}</article>`).join('')
    : `<div class="turing-sealed"><i>⌁</i><b>回答仍处于密封状态</b><p>已锁定 ${secretAnswerCount} 份；存活玩家全部提交后统一揭示，当前不会展示内容或提交者。</p></div>`;
  const exchangePanel = questionEvents.length ? questionEvents.map((event) => {
    const info = describeGameEvent({ primary: event, decision: null }, players);
    return `<article><i>${escapeHtml(info[0])}</i><div><b>${escapeHtml(info[1])}</b><p>${escapeHtml(info[2])}</p></div></article>`;
  }).join('') : '<div class="turing-sealed compact"><b>尚未进入交叉审讯</b><p>怀疑结果揭示后，争议席位会依次接受质询与辩护。</p></div>';
  const playerCards = [...players].sort((left, right) => Number(left.seatNo) - Number(right.seatNo)).map((player) => {
    const seatNo = Number(player.seatNo);
    const revealedIdentity = game.identitiesRevealed ? (player.playerType || player.roleCode) : identityBySeat[seatNo];
    const self = !publicView && seatNo === Number(game.selfSeatNo);
    const identityCopy = revealedIdentity === 'HUMAN' ? '真人' : revealedIdentity === 'AI' ? 'AI' : self ? '你 · 真人' : '身份隐藏';
    const modelCopy = game.identitiesRevealed ? (revealedIdentity === 'HUMAN' ? '真人直接操作' : modelDisplayLabel(player)) : null;
    return `<article class="turing-player-card ${player.status === 'ALIVE' ? 'alive' : 'eliminated'} ${self ? 'self' : ''} ${(game.controversySeatNos || []).map(Number).includes(seatNo) ? 'controversial' : ''}"><header><i>${seatNo}</i><span>${player.status === 'ALIVE' ? '在线' : '已淘汰'}</span></header><h3>${escapeHtml(player.displayName)}</h3><p>${escapeHtml(identityCopy)}</p><small>人格 · ${escapeHtml(player.agentProfileName || '座位默认人格')}</small>${modelCopy ? `<em class="turing-card-model">${escapeHtml(modelCopy)}</em>` : ''}<footer><span>怀疑 ${Number(suspicionCounts[seatNo] || 0)}</span><span>得票 ${Number(voteCounts[seatNo] || 0)}</span></footer></article>`;
  }).join('');
  const rankingPanel = ranking.length ? ranking.map(([seatNo, count], index) => `<article><i>${index + 1}</i><span><b>${escapeHtml(turingSeatDisplay(players, seatNo))}</b><small>${Number(count)} 次首要怀疑</small></span><em style="--score:${Math.min(100, Number(count) * 18)}%"></em></article>`).join('') : '<p>怀疑阶段全部提交后，这里会显示聚合排名。</p>';
  const votePanel = votes.length ? votes.map((vote) => `<span><b>${Number(vote.voterSeatNo)}号</b> → ${Number(vote.targetSeatNo)}号${vote.reasonSummary ? `<small>${escapeHtml(vote.reasonSummary)}</small>` : ''}</span>`).join('')
    : `<div class="turing-sealed compact"><b>选票仍处于密封状态</b><p>已锁定 ${secretVoteCount} 票；不会提前展示任何玩家的投票方向。</p></div>`;
  const feed = [...events].reverse().filter((event) => event.eventType !== 'TURING_PHASE_CHANGED').slice(0, 24).map((event) => {
    const info = describeGameEvent({ primary: event, decision: null }, players);
    return `<article><i>${escapeHtml(info[0])}</i><div><small>第 ${Number(event.roundNo || 0)} 轮 · #${Number(event.sequenceNo || 0)}</small><b>${escapeHtml(info[1])}</b><p>${escapeHtml(info[2])}</p></div></article>`;
  }).join('') || '<div class="turing-sealed compact"><p>启动对局后会出现公开事件。</p></div>';
  const humanTurn = !publicView && game.humanActionRequired === true;
  const controls = publicView ? '<span class="readonly-badge">公开只读观战</span>'
    : game.status === 'WAITING' ? '<button class="primary real-start" type="button">启动潜伏实验</button>'
      : game.status === 'FINISHED' ? '<button class="primary rematch-game" type="button">同配置重赛</button>'
        : `<span class="auto-run-status ${autoPlayRetrying ? 'retrying' : ''}" ${autoPlayStatus ? '' : 'hidden'}>${escapeHtml(autoPlayStatus)}</span><select class="auto-speed" aria-label="自动运行速度"><option value="2200" ${autoPlayDelay === 2200 ? 'selected' : ''}>慢速观察</option><option value="800" ${autoPlayDelay === 800 ? 'selected' : ''}>标准速度</option><option value="250" ${autoPlayDelay === 250 ? 'selected' : ''}>快速模拟</option></select><button class="secondary real-advance" ${humanTurn || autoPlayEnabled || isTurnInFlight() ? 'disabled' : ''}>${humanTurn ? '等待你操作' : isTurnInFlight() ? '模型行动中…' : '推进一步'}</button><button class="primary real-auto" ${humanTurn || manualStepInFlight ? 'disabled' : ''}>${humanTurn ? '请先完成操作' : autoPlayEnabled ? (autoPlayInFlight ? '暂停模拟 · 行动中' : '暂停模拟') : '自动模拟'}</button>`;
  const currentActor = game.status === 'WAITING' ? '等待房主启动' : game.status === 'FINISHED' ? '所有身份已经揭晓' : humanTurn ? '轮到你完成秘密操作' : game.currentActorName || '系统正在结算阶段';
  dashboard.classList.remove('werewolf-watch-dashboard');
  dashboard.classList.add('turing-watch-dashboard');
  dashboard.innerHTML = `<header class="real-watch-header turing-header"><button class="back-button real-back" aria-label="返回竞技大厅">←</button><div class="match-identity"><span class="status-live"><i></i> ${escapeHtml(gameStatusLabel(game.status))}</span><h1>${escapeHtml(game.name)} <small>#${Number(game.id)}</small></h1><p>图灵潜伏局 · 第 ${roundNo} / ${Number(game.maxRounds || 3)} 轮 · ${escapeHtml(turingPhaseLabel(game.currentPhase))}</p></div><div class="real-watch-actions"><span class="stream-state">${escapeHtml(liveStreamStatus)}</span><button class="secondary real-replay" type="button">查看复盘</button>${game.publicGame ? '<button class="secondary real-share" type="button">复制公开链接</button>' : ''}${controls}</div></header>
    <section class="turing-turn-stage"><div><small>${escapeHtml(turingPhaseLabel(game.currentPhase))} · ${escapeHtml(game.currentActionType || '阶段推进')}</small><h2>${escapeHtml(currentActor)}</h2><p>${humanTurn ? '只有你的表单会在此视角出现；提交内容将在规则规定的揭示节点公开。' : game.status === 'RUNNING' ? '下一步行动由服务器校验并持久化，秘密内容不会提前进入回放。' : escapeHtml(gameStatusLabel(game.status))}</p></div><aside><span>存活 <b>${Number(game.alivePlayerCount ?? players.filter((player) => player.status === 'ALIVE').length)}</b> / 6</span><span>误淘汰 AI <b>${Number(game.wrongAiEliminationCount || 0)}</b> / 2</span></aside></section>
    ${watchActionError ? `<div class="watch-action-error" role="alert"><b>操作未完成</b><span>${escapeHtml(watchActionError)}</span><button type="button" data-dismiss-watch-error>×</button></div>` : ''}
    <nav class="turing-phase-rail">${[['ANSWER_COLLECTION', '匿名回答'], ['SUSPICION_COLLECTION', '秘密怀疑'], ['CONTROVERSY_REVEAL', '争议揭示'], ['QUESTION', '质询回应'], ['DEFENSE', '公开辩护'], ['VOTE_COLLECTION', '秘密投票'], ['ROUND_RESULT', '身份结算']].map(([phase, label]) => `<span class="${game.currentPhase === phase || (phase === 'QUESTION' && game.currentPhase === 'RESPONSE') || (phase === 'VOTE_COLLECTION' && game.currentPhase === 'VOTE_REVEAL') ? 'active' : ''}">${escapeHtml(label)}</span>`).join('')}</nav>
    <main class="turing-board"><section class="turing-main-column"><article class="turing-question"><span>ROUND ${String(roundNo).padStart(2, '0')} · OPEN PROMPT</span><h2>${escapeHtml(game.currentQuestion || '启动后生成本轮问题')}</h2><p>请从回答内容判断谁更像真人；名字、模型与内部玩家 ID 不会提供线索。</p></article><section class="turing-panel"><header><div><small>SIMULTANEOUS REVEAL</small><h2>匿名回答墙</h2></div><span>${answers.length ? `${answers.length} 份已揭示` : `${secretAnswerCount} 份已锁定`}</span></header><div class="turing-answer-grid">${answerPanel}</div></section><section class="turing-panel"><header><div><small>CROSS EXAMINATION</small><h2>质询与辩护</h2></div><span>${questionEvents.length} 条公开内容</span></header><div class="turing-exchange-list">${exchangePanel}</div></section><section class="turing-panel"><header><div><small>SECRET BALLOT</small><h2>${voteReveal?.payload?.revote ? '重投票型' : '本轮投票'}</h2></div><span>${votes.length ? '已统一揭票' : `${secretVoteCount} 票已锁定`}</span></header><div class="turing-vote-flow">${votePanel}</div></section>${renderTuringFinish(game, players)}</section>
      <aside class="turing-side-column"><section class="turing-panel turing-player-panel"><header><div><small>ANONYMOUS LINEUP</small><h2>六席身份矩阵</h2></div><span>${game.identitiesRevealed ? '全部揭晓' : '身份隐藏中'}</span></header><div class="turing-player-grid">${playerCards}</div></section><section class="turing-panel turing-ranking"><header><div><small>PUBLIC SUSPICION</small><h2>怀疑聚合</h2></div><span>${(game.controversySeatNos || []).length ? `争议 ${game.controversySeatNos.join('、')} 号` : '等待揭示'}</span></header><div>${rankingPanel}</div></section><section class="turing-action-dock">${renderTuringHumanAction(game, players, publicView)}</section></aside></main>
    <section class="turing-public-feed"><header><div><small>PUBLIC EVENT LEDGER</small><h2>公开事件账本</h2></div><span>${events.length} 条安全事件</span></header><div>${feed}</div></section>`;
  dashboard.querySelector('.real-back')?.addEventListener('click', () => goTo('home'));
  dashboard.querySelector('.real-start')?.addEventListener('click', startCurrentGame);
  dashboard.querySelector('.real-advance')?.addEventListener('click', () => advanceCurrentGame(false));
  dashboard.querySelector('.real-auto')?.addEventListener('click', toggleAutoPlay);
  dashboard.querySelector('.real-replay')?.addEventListener('click', () => goTo('replays'));
  dashboard.querySelector('[data-open-replay]')?.addEventListener('click', () => goTo('replays'));
  dashboard.querySelector('[data-finish-home]')?.addEventListener('click', () => goTo('home'));
  dashboard.querySelector('.real-share')?.addEventListener('click', shareCurrentGame);
  dashboard.querySelector('.rematch-game')?.addEventListener('click', (event) => rematchCurrentGame(event.currentTarget));
  dashboard.querySelector('.auto-speed')?.addEventListener('change', (event) => { autoPlayDelay = Number(event.target.value); });
  dashboard.querySelector('[data-dismiss-watch-error]')?.addEventListener('click', () => { watchActionError = ''; renderTuringHuntWatch(game, players, events, publicView); });
  dashboard.querySelector('[data-turing-human-form]')?.addEventListener('submit', submitTuringHumanAction);
  updateLiveStreamIndicator();
}

function renderTuringHuntWatch(game, players, events, publicView = false) {
  const dashboard = ensureWatchDashboard();
  if (!dashboard) return;
  const retainedArena3D = detachArena3DHost(dashboard, 'turing');
  const roundNo = Number(game.currentRound || 1);
  const stageIndex = turingStageIndex(game.currentPhase);
  const completed = Number(game.submissionCompletedCount || 0);
  const required = Number(game.submissionRequiredCount || 0);
  const stageRatio = required > 0 ? Math.min(1, completed / required) : (game.status === 'FINISHED' ? 1 : 0);
  const overallProgress = game.status === 'FINISHED' ? 100 : Math.round((stageIndex + stageRatio) / TURING_STAGES.length * 100);
  const humanTurn = !publicView && game.humanActionRequired === true;
  const phaseStatus = game.status === 'WAITING' ? '等待启动' : game.status === 'FINISHED'
    ? '身份已揭晓' : humanTurn ? '等待你提交' : '本阶段进行中';
  const controls = publicView ? '<span class="readonly-badge">公开只读</span>'
    : game.status === 'WAITING' ? '<button class="primary real-start" type="button">启动对局</button>'
      : game.status === 'FINISHED' ? '<button class="secondary real-replay" type="button">完整复盘</button><button class="primary rematch-game" type="button">同配置重赛</button>'
        : `<button class="secondary real-advance" ${humanTurn || autoPlayEnabled || isTurnInFlight() ? 'disabled' : ''}>${humanTurn ? '等待你操作' : isTurnInFlight() ? '阶段处理中…' : '推进阶段'}</button><button class="primary real-auto" ${humanTurn || manualStepInFlight ? 'disabled' : ''}>${humanTurn ? '请先提交' : autoPlayEnabled ? '暂停自动运行' : '自动运行'}</button>`;
  const rail = TURING_STAGES.map(([label, english], index) => {
    const stateClass = index === stageIndex ? 'current' : index < stageIndex ? 'completed' : 'future';
    return `<button type="button" class="${stateClass}" ${index < stageIndex ? 'data-open-turing-history' : 'disabled'} ${index === stageIndex ? 'aria-current="step"' : ''}><i>${index < stageIndex ? '✓' : index + 1}</i><span><b>${escapeHtml(label)}</b><small>${escapeHtml(english)}</small></span></button>`;
  }).join('');
  dashboard.classList.remove('werewolf-watch-dashboard');
  dashboard.classList.add('turing-watch-dashboard');
  dashboard.innerHTML = `<div class="turing-shell" data-turing-mobile-tab="${escapeHtml(turingMobileTab)}"><header class="turing-topbar"><button class="back-button real-back" aria-label="返回竞技大厅">←</button><div class="turing-match-title"><span class="status-live"><i></i> ${escapeHtml(gameStatusLabel(game.status))}</span><h1>${escapeHtml(game.name)} <small>#${Number(game.id)}</small></h1><p>第 ${roundNo} / ${Number(game.maxRounds || 3)} 轮 · ${escapeHtml(turingPhaseLabel(game.currentPhase))}</p></div><div class="turing-top-actions"><span>${escapeHtml(phaseStatus)}</span><button class="secondary" type="button" data-open-turing-history>阶段记录</button>${game.publicGame ? '<button class="secondary real-share" type="button">复制公开链接</button>' : ''}</div></header>
    <nav class="turing-stage-rail" aria-label="本轮阶段">${rail}</nav>
    <nav class="turing-mobile-tabs" aria-label="移动端视图"><button class="${turingMobileTab === 'game' ? 'active' : ''}" data-turing-tab="game">对局</button><button class="${turingMobileTab === 'players' ? 'active' : ''}" data-turing-tab="players">玩家</button><button data-turing-tab="history">记录</button></nav>
    ${watchActionError ? '<div class="watch-action-error" role="alert"><b>阶段暂未完成</b><span>请稍后重新推进，本局公开信息没有变化。</span><button type="button" data-dismiss-watch-error>×</button></div>' : ''}
    <main class="turing-stage-layout"><section class="turing-stage-main"><div class="arena3d-host turing-3d-host" data-arena-3d="turing" aria-label="可旋转的 3D 图灵身份审讯舞台"></div><div class="turing-stage-content">${renderTuringStage(game, players, events, publicView)}</div></section><aside class="turing-stage-sidebar">${renderTuringSeatSidebar(game, players)}</aside></main>
    <footer class="turing-bottom-bar"><div class="turing-overall-progress"><span><b>${escapeHtml(TURING_STAGES[stageIndex][0])}</b><small>${required ? `匿名进度 ${completed} / ${required}` : phaseStatus}</small></span><div><i style="--progress:${overallProgress}%"></i></div><em>${overallProgress}%</em></div><div class="turing-bottom-controls">${controls}</div></footer>
    ${renderTuringHistory(events, players)}</div>`;
  restoreArena3DHost(dashboard, 'turing', retainedArena3D);
  dashboard.querySelector('.real-back')?.addEventListener('click', () => goTo('home'));
  dashboard.querySelector('.real-start')?.addEventListener('click', startCurrentGame);
  dashboard.querySelector('.real-advance')?.addEventListener('click', () => advanceCurrentGame(false));
  dashboard.querySelector('.real-auto')?.addEventListener('click', toggleAutoPlay);
  dashboard.querySelectorAll('.real-replay').forEach((button) => button.addEventListener('click', () => goTo('replays')));
  dashboard.querySelector('.real-share')?.addEventListener('click', shareCurrentGame);
  dashboard.querySelector('.rematch-game')?.addEventListener('click', (event) => rematchCurrentGame(event.currentTarget));
  dashboard.querySelector('[data-dismiss-watch-error]')?.addEventListener('click', () => { watchActionError = ''; renderTuringHuntWatch(game, players, events, publicView); });
  dashboard.querySelector('[data-turing-human-form]')?.addEventListener('submit', submitTuringHumanAction);
  dashboard.querySelector('[data-turing-text-input]')?.addEventListener('input', (event) => updateTuringFormValidation(event.currentTarget.form));
  updateTuringFormValidation(dashboard.querySelector('[data-turing-human-form]'));
  dashboard.querySelectorAll('[data-expand-answer]').forEach((button) => button.addEventListener('click', () => {
    const card = button.closest('.turing-answer-card');
    card?.classList.toggle('expanded');
    button.textContent = card?.classList.contains('expanded') ? '收起全文' : '展开全文';
  }));
  dashboard.querySelectorAll('[data-open-turing-history]').forEach((button) => button.addEventListener('click', () => {
    turingHistoryOpen = true;
    renderTuringHuntWatch(game, players, events, publicView);
  }));
  dashboard.querySelectorAll('[data-close-turing-history]').forEach((button) => button.addEventListener('click', () => {
    turingHistoryOpen = false;
    if (turingMobileTab === 'history') turingMobileTab = 'game';
    renderTuringHuntWatch(game, players, events, publicView);
  }));
  dashboard.querySelectorAll('[data-turing-tab]').forEach((button) => button.addEventListener('click', () => {
    const tab = button.dataset.turingTab;
    if (tab === 'history') {
      turingMobileTab = 'history';
      turingHistoryOpen = true;
    } else {
      turingMobileTab = tab;
      turingHistoryOpen = false;
    }
    renderTuringHuntWatch(game, players, events, publicView);
  }));
  mountArena3D(dashboard, 'turing', { game, players, events, publicView });
  updateLiveStreamIndicator();
}

function renderRealWatch(game, players, events, publicView = false) {
  if (!samePlayerId(lastArenaGameId, game.id)) {
    selectedArenaPlayerId = null;
    selectedTimelineSequence = null;
    lastArenaGameId = game.id;
  }
  if (game.mode === 'TURING_HUNT') {
    renderTuringHuntWatch(game, players, events, publicView);
    return;
  }
  const dashboard = ensureWatchDashboard();
  if (!dashboard) return;
  const eventGroups = groupGameEvents(events);
  const reportGroups = reportGroupsForTab(eventGroups, selectedReportTab);
  const isBattle = game.mode === 'BATTLE_ROYALE';
  const arenaMode = isBattle ? 'battle' : 'werewolf';
  if (!isBattle && selectedMobileArenaTab === 'players') selectedMobileArenaTab = 'arena';
  const reportContent = selectedReportTab === 'commentary'
    ? renderServerCommentary(currentMatchProjection, game.id)
    : reportGroups.length ? renderReportCards(reportGroups, players, isBattle)
      : `<div class="real-empty">${selectedReportTab === 'decisions' ? '暂无可见的策略摘要。' : '推进对局后，这里会出现事件。'}</div>`;
  const gameStage = isBattle
    ? renderBattleMap(players, game, eventGroups)
    : renderWerewolfArenaV2(players, game, eventGroups, publicView);
  const selectedPlayer = findArenaPlayer(players, selectedArenaPlayerId);
  const latestGroup = eventGroups[eventGroups.length - 1];
  const actorName = game.status === 'WAITING' ? '等待房主启动'
    : game.status === 'FINISHED' ? '对局已完成'
      : game.currentActorName || '系统正在结算阶段';
  const actorModel = game.status === 'RUNNING' ? currentActorModelLabel(game) : '';
  const actorPlayer = findArenaPlayer(players, game.currentActorPlayerId);
  const actorPersona = game.currentActorPersonaName || actorPlayer?.agentProfileName || '';
  const stageClass = `phase-${String(game.currentPhase || 'waiting').toLowerCase()} importance-${eventImportance(latestGroup || {})}`;
  const ownerActions = publicView ? '' : `<button class="secondary save-lineup-template" type="button">保存阵容</button>${game.status === 'FINISHED' ? '<button class="primary rematch-game" type="button">同配置重赛</button>' : ''}`;
  const controls = publicView
    ? '<span class="readonly-badge">公开只读观战</span>'
    : game.status === 'WAITING'
      ? '<button class="primary real-start" type="button">启动对局</button>'
      : game.status === 'FINISHED'
        ? ''
        : `<span class="auto-run-status ${autoPlayRetrying ? 'retrying' : ''}" ${autoPlayStatus ? '' : 'hidden'}>${escapeHtml(autoPlayStatus)}</span><select class="auto-speed" aria-label="自动运行速度"><option value="2200" ${autoPlayDelay === 2200 ? 'selected' : ''}>慢速观察</option><option value="800" ${autoPlayDelay === 800 ? 'selected' : ''}>标准速度</option><option value="250" ${autoPlayDelay === 250 ? 'selected' : ''}>快速模拟</option></select><button class="secondary real-advance" ${autoPlayEnabled || isTurnInFlight() ? 'disabled' : ''}>${isTurnInFlight() ? '模型行动中…' : '推进一步'}</button><button class="primary real-auto" ${manualStepInFlight ? 'disabled' : ''}>${autoPlayEnabled ? (autoPlayInFlight ? '暂停模拟 · 行动中' : '暂停模拟') : manualStepInFlight ? '单步执行中' : '自动模拟'}</button>`;
  dashboard.classList.remove('turing-watch-dashboard');
  dashboard.classList.toggle('werewolf-watch-dashboard', !isBattle);
  const retainedArena3D = detachArena3DHost(dashboard, arenaMode);
  dashboard.innerHTML = `
    <header class="real-watch-header">
      <button class="back-button real-back" aria-label="返回竞技大厅">←</button>
      <div class="match-identity"><span class="status-live"><i></i> ${escapeHtml(gameStatusLabel(game.status))}</span><h1>${escapeHtml(game.name)} <small>#${Number(game.id)}</small></h1><p>${escapeHtml(gameModeLabel(game.mode))} · 第 ${Number(game.currentRound || 1)} ${isBattle ? '轮' : '天'} · ${isBattle ? '生存行动' : escapeHtml(werewolfPhaseLabel(game.currentPhase))}</p></div>
      <div class="real-watch-actions"><span class="stream-state">${escapeHtml(liveStreamStatus)}</span><button class="secondary real-replay" type="button">查看复盘</button>${ownerActions}${game.publicGame ? '<button class="secondary real-share" type="button">复制公开链接</button>' : ''}${controls}</div>
    </header>
    <section class="current-turn-stage ${stageClass}" aria-live="polite"><div><small>第 ${Number(game.currentRound || 1)} ${isBattle ? '轮' : '天'} · ${escapeHtml(isBattle ? game.currentActionType || '生存行动' : werewolfPhaseLabel(game.currentPhase))}</small><h2>${escapeHtml(actorName)}</h2><p>${actorModel ? `${escapeHtml(actorModel)}${actorPersona ? ` · ${escapeHtml(actorPersona)}` : ''} · ` : actorPersona ? `${escapeHtml(actorPersona)} · ` : ''}${isTurnInFlight() ? '正在生成并校验下一步行动' : game.status === 'RUNNING' ? '等待执行下一步合法行动' : escapeHtml(gameStatusLabel(game.status))}</p></div><span class="thinking-indicator ${isTurnInFlight() ? 'active' : ''}"><i></i>${isTurnInFlight() ? '思考中' : '就绪'}<em class="thinking-elapsed" ${isTurnInFlight() ? '' : 'hidden'}></em></span></section>
    ${watchActionError ? `<div class="watch-action-error" role="alert"><b>操作未完成</b><span>${escapeHtml(watchActionError)}</span><button type="button" data-dismiss-watch-error>×</button></div>` : ''}
    <nav class="mobile-arena-tabs ${isBattle ? '' : 'werewolf-mobile-tabs'}" aria-label="观战内容"><button class="${selectedMobileArenaTab === 'arena' ? 'active' : ''}" data-mobile-arena="arena">主舞台</button>${isBattle ? `<button class="${selectedMobileArenaTab === 'players' ? 'active' : ''}" data-mobile-arena="players">玩家详情</button>` : ''}<button class="${selectedMobileArenaTab === 'report' ? 'active' : ''}" data-mobile-arena="report">赛事解说</button></nav>
    <div class="real-watch-grid ${isBattle ? 'battle-watch-grid' : 'werewolf-watch-grid'}">
      <section class="real-player-panel arena-board ${isBattle ? 'battle-board' : 'werewolf-board'} mobile-section-${selectedMobileArenaTab === 'arena' ? 'active' : 'hidden'}"><div class="real-section-head"><div><small class="arena-kicker">LIVE ARENA</small><h2>${isBattle ? '战术区域态势' : '狼人杀推理圆桌'}</h2></div><span>${players.length} 名玩家 · ${isBattle ? '五个离散区域，不表达实际距离' : '发言、投票与阶段行动实时可视化'}</span></div>${isBattle ? renderRegionSituation(players, eventGroups) : ''}<div class="arena-stage-wrap">${gameStage}${renderEventMoment(latestGroup, players)}${finishedPresentation(game, eventGroups, players)}</div></section>
      ${isBattle ? `<aside class="arena-player-column mobile-section-${selectedMobileArenaTab === 'players' ? 'active' : 'hidden'}">${renderPlayerDetail(selectedPlayer, game, events, publicView)}</aside>` : ''}
      <aside class="real-event-panel mobile-section-${selectedMobileArenaTab === 'report' ? 'active' : 'hidden'}" id="arena-event-panel"><div class="real-section-head"><div><small class="arena-kicker">MATCH FEED</small><h2>赛事内容</h2></div><span>${eventGroups.length} 次行动 · ${events.length} 条事件</span></div><div class="report-tabs" role="tablist"><button class="${selectedReportTab === 'commentary' ? 'active' : ''}" data-report-tab="commentary">赛事解说</button><button class="${selectedReportTab === 'decisions' ? 'active' : ''}" data-report-tab="decisions">策略摘要</button><button class="${selectedReportTab === 'logs' ? 'active' : ''}" data-report-tab="logs">完整日志</button></div><div class="real-event-list">
        ${reportContent}
      </div></aside>
    </div>
    ${isBattle ? `${renderArenaTimeline(eventGroups, players)}${renderTimelineSelection(eventGroups, players)}` : ''}
    ${!isBattle && selectedPlayer ? `<div class="arena-player-drawer-backdrop" data-close-player></div><aside class="arena-player-drawer" role="dialog" aria-modal="true" aria-label="${escapeHtml(selectedPlayer.displayName)} 玩家详情">${renderPlayerDetail(selectedPlayer, game, events, publicView)}</aside>` : ''}`;
  restoreArena3DHost(dashboard, arenaMode, retainedArena3D);
  dashboard.querySelector('.real-back')?.addEventListener('click', () => goTo('home'));
  dashboard.querySelector('.real-start')?.addEventListener('click', startCurrentGame);
  dashboard.querySelector('.real-advance')?.addEventListener('click', () => advanceCurrentGame(false));
  dashboard.querySelector('.real-auto')?.addEventListener('click', toggleAutoPlay);
  dashboard.querySelector('.real-replay')?.addEventListener('click', () => goTo('replays'));
  dashboard.querySelector('[data-open-replay]')?.addEventListener('click', () => goTo('replays'));
  dashboard.querySelector('[data-finish-home]')?.addEventListener('click', () => goTo('home'));
  dashboard.querySelector('.real-share')?.addEventListener('click', shareCurrentGame);
  dashboard.querySelector('.save-lineup-template')?.addEventListener('click', saveCurrentLineupTemplate);
  dashboard.querySelector('.rematch-game')?.addEventListener('click', (event) => rematchCurrentGame(event.currentTarget));
  dashboard.querySelectorAll('[data-share-clip]').forEach((button) => button.addEventListener('click', () => shareHighlightClip(button.dataset.shareGame, button.dataset.shareClip)));
  dashboard.querySelector('.auto-speed')?.addEventListener('change', (event) => { autoPlayDelay = Number(event.target.value); });
  dashboard.querySelectorAll('[data-report-tab]').forEach((button) => button.addEventListener('click', () => { selectedReportTab = button.dataset.reportTab; renderRealWatch(game, players, events, publicView); }));
  dashboard.querySelectorAll('[data-mobile-arena]').forEach((button) => button.addEventListener('click', () => { selectedMobileArenaTab = button.dataset.mobileArena; renderRealWatch(game, players, events, publicView); }));
  dashboard.querySelector('[data-dismiss-watch-error]')?.addEventListener('click', () => { watchActionError = ''; renderRealWatch(game, players, events, publicView); });
  dashboard.querySelectorAll('[data-close-player]').forEach((button) => button.addEventListener('click', () => { selectedArenaPlayerId = null; renderRealWatch(game, players, events, publicView); }));
  dashboard.querySelector('[data-close-timeline]')?.addEventListener('click', () => { selectedTimelineSequence = null; renderRealWatch(game, players, events, publicView); });
  dashboard.querySelectorAll('[data-timeline-sequence]').forEach((button) => button.addEventListener('click', () => { selectedTimelineSequence = Number(button.dataset.timelineSequence); renderRealWatch(game, players, events, publicView); }));
  dashboard.querySelectorAll('[data-arena-player]').forEach((playerButton) => {
    playerButton.addEventListener('click', () => {
      const playerId = Number(playerButton.dataset.arenaPlayer);
      selectedArenaPlayerId = samePlayerId(selectedArenaPlayerId, playerId) ? null : playerId;
      renderRealWatch(game, players, events, publicView);
    });
  });
  dashboard.querySelectorAll('[data-wolf-relation]').forEach((button) => button.addEventListener('click', () => {
    selectedWerewolfRelation = button.dataset.wolfRelation;
    renderRealWatch(game, players, events, publicView);
  }));
  dashboard.querySelectorAll('[data-audience-action]').forEach((button) => button.addEventListener('click', () => {
    updateWerewolfAudience(game, button.dataset.audienceAction, Number(button.dataset.seatNo));
    renderRealWatch(game, players, events, publicView);
  }));
  mountArena3D(dashboard, isBattle ? 'battle' : 'werewolf', {
    game,
    players,
    events,
    publicView,
    projection: samePlayerId(currentMatchProjection?.gameId, game.id) ? currentMatchProjection : null,
    selectedRelation: selectedWerewolfRelation,
    onSelectPlayer: (playerId) => {
      selectedArenaPlayerId = samePlayerId(selectedArenaPlayerId, playerId) ? null : playerId;
      renderRealWatch(game, players, events, publicView);
    }
  });
  updateLiveStreamIndicator();
  updateThinkingClock();
}

async function startCurrentGame() {
  const session = getGameSession();
  if (!session || session.publicView || !lastRealGame) return;
  const button = document.querySelector('.real-start');
  if (button) { button.disabled = true; button.textContent = '正在启动…'; }
  watchActionError = '';
  try {
    const path = lastRealGame.mode === 'WEREWOLF' ? 'start' : lastRealGame.mode === 'TURING_HUNT' ? 'turing-start' : 'battle-start';
    await apiRequest(`/api/games/${session.gameId}/${path}`, { method: 'POST' });
    showToast('对局已启动');
    await loadCurrentGame();
  } catch (error) {
    watchActionError = lastRealGame?.mode === 'TURING_HUNT' ? '阶段暂未完成' : error.message;
    renderRealWatch(lastRealGame, lastRealPlayers, currentRealEvents, false);
  }
}

async function advanceCurrentGame(fromAutoPlay = false) {
  const session = getGameSession();
  if (!session) {
    if (!fromAutoPlay) showToast('请先创建一场对局');
    return fromAutoPlay ? { continued: false, error: new Error('请先创建一场对局') } : false;
  }
  if (session.publicView) {
    if (!fromAutoPlay) showToast('公开观战为只读模式');
    return fromAutoPlay ? { continued: false, error: new Error('公开观战为只读模式') } : false;
  }
  if (lastRealGame?.mode === 'TURING_HUNT' && lastRealGame?.humanActionRequired) {
    const outcome = { continued: false, humanRequired: true };
    if (!fromAutoPlay) showToast('请先完成你的真人席位操作');
    return fromAutoPlay ? outcome : false;
  }
  const advanceButton = document.querySelector('.real-advance');
  if (advanceButton) { advanceButton.disabled = true; advanceButton.textContent = session.mode === 'TURING_HUNT' ? '阶段处理中…' : '模型行动中…'; }
  watchActionError = '';
  if (!fromAutoPlay) manualStepInFlight = true;
  turnStartedAt = Date.now();
  updateAutoPlayControls();
  try {
    const path = session.mode === 'WEREWOLF' ? 'next-turn' : session.mode === 'TURING_HUNT' ? 'turing-next-turn' : 'battle-next-turn';
    await apiRequest(`/api/games/${session.gameId}/${path}`, { method: 'POST', timeoutMs: 70000 });
    const game = await loadCurrentGame(true);
    if (game?.status === 'FINISHED') return fromAutoPlay ? { continued: false, finished: true } : false;
    if (game?.mode === 'TURING_HUNT' && game?.humanActionRequired) return fromAutoPlay ? { continued: false, humanRequired: true } : true;
    return fromAutoPlay ? { continued: true } : true;
  } catch (error) {
    if (!fromAutoPlay) showToast(session.mode === 'TURING_HUNT' ? '阶段暂未完成，请稍后重试' : `推进失败：${error.message}`);
    watchActionError = session.mode === 'TURING_HUNT' ? '阶段暂未完成' : error.message;
    if (!fromAutoPlay && lastRealGame) renderRealWatch(lastRealGame, lastRealPlayers, currentRealEvents, false);
    return fromAutoPlay ? { continued: false, error } : false;
  } finally {
    if (!fromAutoPlay) manualStepInFlight = false;
    turnStartedAt = null;
    if (!fromAutoPlay && advanceButton) { advanceButton.disabled = false; advanceButton.textContent = session.mode === 'TURING_HUNT' ? '推进阶段' : '推进一步'; }
    updateAutoPlayControls();
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

let lobbyGames = [];
let lobbyPage = 0;
let lobbyHasMore = false;
const LOBBY_PAGE_SIZE = 20;

function renderLobbyGames(games) {
  const grid = document.getElementById('live-game-grid');
  const liveLabel = document.getElementById('lobby-live-count');
  const totalLabel = document.getElementById('lobby-game-count');
  const continueButton = document.getElementById('continue-latest-game');
  if (continueButton) continueButton.hidden = false;
  let moreButton = document.getElementById('lobby-load-more');
  if (grid && !moreButton) {
    moreButton = document.createElement('button');
    moreButton.id = 'lobby-load-more';
    moreButton.type = 'button';
    moreButton.className = 'secondary';
    moreButton.textContent = '加载更多对局';
    moreButton.addEventListener('click', loadMoreLobbyGames);
    grid.after(moreButton);
  }
  if (moreButton) moreButton.hidden = !lobbyHasMore;
  const runningCount = games.filter((game) => game.status === 'RUNNING').length;
  if (liveLabel) liveLabel.textContent = runningCount ? `${runningCount} 场真实对局正在进行` : '当前没有运行中的对局';
  if (totalLabel) totalLabel.textContent = `${lobbyHasMore ? '已加载' : '共'} ${games.length} 场 · 最近更新优先`;
  if (!grid) return;
  if (!games.length) {
    grid.innerHTML = '<div class="lobby-empty"><span>⌁</span><h3>还没有真实对局</h3><p>创建狼人杀、大逃杀或图灵潜伏局，行动、公开事件和复盘都会自动记录。</p><button class="primary" type="button" data-create-first>创建第一场对局</button></div>';
    grid.querySelector('[data-create-first]')?.addEventListener('click', () => goTo('create'));
    if (continueButton) {
      continueButton.disabled = false;
      continueButton.onclick = () => goTo('create');
      continueButton.innerHTML = '创建第一场对局 <span>↗</span>';
    }
    return;
  }
  grid.innerHTML = games.map((game, index) => {
    const battle = game.mode === 'BATTLE_ROYALE';
    const turing = game.mode === 'TURING_HUNT';
    const statusClass = String(game.status || '').toLowerCase();
    const statusText = gameStatusLabel(game.status);
    const phaseText = battle ? `第 ${game.currentRound || 1} 轮 · 生存行动` : turing ? `第 ${game.currentRound || 1} / ${game.maxRounds || 3} 轮 · ${turingPhaseLabel(game.currentPhase)}` : `${werewolfPhaseLabel(game.currentPhase)} · 第 ${game.currentRound || 1} 轮`;
    const updated = game.updateTime ? new Date(game.updateTime).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '刚刚更新';
    return `<article class="game-card real-lobby-card ${index === 0 ? 'featured' : ''}" data-lobby-index="${index}" tabindex="0" role="button">
      <div class="card-cover ${battle ? 'island-cover' : turing ? 'turing-cover' : 'werewolf-cover'}"><span class="status-live status-${statusClass}"><i></i>${escapeHtml(statusText)}</span><span class="spectators">#${game.id}</span>${battle ? '<div class="map-nodes"><i></i><i></i><i></i><i></i><i></i></div>' : `<div class="cover-symbol">${turing ? '⌁' : '◒'}</div>`}<div class="round-tag">ROUND ${String(game.currentRound || 1).padStart(2, '0')}</div></div>
      <div class="game-card-body"><div class="game-meta"><span>${turing ? '图灵潜伏局' : battle ? 'AI 大逃杀' : 'AI 狼人杀'}</span><small>${game.maxPlayers || (turing ? 6 : 8)} 名玩家 · ${escapeHtml(updated)}</small></div><h3>${escapeHtml(game.name)}</h3><p>${escapeHtml(phaseText)}，数据来自真实后端状态与事件记录。</p><div class="seat-row"><b>${game.publicGame ? '公开观战' : '私有对局'}</b><em>${game.status === 'FINISHED' ? '查看复盘 →' : '进入观战 →'}</em></div></div>
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
    const games = await apiRequest(`/api/games?page=0&size=${LOBBY_PAGE_SIZE}`);
    lobbyGames = games || [];
    lobbyPage = 0;
    lobbyHasMore = lobbyGames.length === LOBBY_PAGE_SIZE;
    renderLobbyGames(lobbyGames);
  } catch (error) {
    const liveLabel = document.getElementById('lobby-live-count');
    const totalLabel = document.getElementById('lobby-game-count');
    const continueButton = document.getElementById('continue-latest-game');
    if (liveLabel) liveLabel.textContent = '真实对局暂未连接，60 秒演示仍可直接观看';
    if (totalLabel) totalLabel.textContent = '未连接';
    if (continueButton) continueButton.hidden = true;
    if (grid) grid.innerHTML = `<div class="lobby-empty error"><span>⌁</span><h3>先看一场 AI 如何做选择</h3><p>真实对局暂时无法加载。演示无需后端或模型配置，可以直接观看。</p><a class="primary" href="/demo.html?autoplay=1" style="text-decoration:none">观看 60 秒演示 ↗</a><button class="text-button" type="button" data-retry-lobby>重新连接真实对局</button></div>`;
    grid?.querySelector('[data-retry-lobby]')?.addEventListener('click', loadLobbyGames);
  }
}

async function loadMoreLobbyGames() {
  const button = document.getElementById('lobby-load-more');
  if (!lobbyHasMore || button?.disabled) return;
  if (button) button.disabled = true;
  try {
    const nextPage = lobbyPage + 1;
    const games = await apiRequest(`/api/games?page=${nextPage}&size=${LOBBY_PAGE_SIZE}`);
    lobbyGames = [...new Map([...lobbyGames, ...(games || [])].map((game) => [game.id, game])).values()];
    lobbyPage = nextPage;
    lobbyHasMore = (games || []).length === LOBBY_PAGE_SIZE;
    renderLobbyGames(lobbyGames);
  } catch (error) {
    showToast(`加载更多对局失败：${error.message}`);
  } finally {
    if (button) button.disabled = false;
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

function renderAgentProfiles(profiles) {
  const container = document.getElementById('saved-agent-list');
  if (!container) return;
  container.innerHTML = profiles.length ? profiles.map((profile) => `<article class="saved-agent-card" style="--agent-color:${escapeHtml(profile.accentColor || '#8B7CFF')}">
    <i>${escapeHtml(profile.avatarEmoji || 'AI')}</i><div><b>${escapeHtml(profile.name)}</b><span>${escapeHtml((profile.traits || []).join(' · ') || '未设置标签')}</span><small>${escapeHtml(profile.speakingStyle)} · ${escapeHtml(riskPreferenceLabel(profile.riskPreference))}</small></div>
    <button class="text-button delete-agent-button" data-profile-id="${Number(profile.id)}" data-profile-name="${escapeHtml(profile.name)}" type="button">删除</button>
  </article>`).join('') : '<p>还没有 Agent 人格，请在左侧创建。</p>';
}

async function loadAgentProfiles() {
  try {
    await ensureVisitorSession();
    renderAgentProfiles(await apiRequest('/api/agent-profiles'));
  } catch (error) {
    showToast(`加载人格失败：${error.message}`);
  }
}

document.getElementById('saved-agent-list')?.addEventListener('click', async (event) => {
  const button = event.target.closest('.delete-agent-button');
  if (!button || !window.confirm(`确定删除“${button.dataset.profileName || '该人格'}”吗？历史对局不会受影响。`)) return;
  button.disabled = true;
  try {
    await apiRequest(`/api/agent-profiles/${button.dataset.profileId}`, { method: 'DELETE' });
    showToast('Agent 人格已删除');
    await loadAgentProfiles();
  } catch (error) {
    showToast(`删除失败：${error.message}`);
    button.disabled = false;
  }
});

document.getElementById('agent-profile-form')?.addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const button = form.querySelector('button[type="submit"]');
  button.disabled = true;
  button.textContent = '正在保存…';
  try {
    const traits = document.getElementById('agent-profile-traits').value.split(/[,，]/)
      .map((item) => item.trim()).filter(Boolean).slice(0, 5);
    await apiRequest('/api/agent-profiles', { method: 'POST', body: JSON.stringify({
      name: document.getElementById('agent-profile-name').value.trim(),
      description: document.getElementById('agent-profile-description').value.trim() || null,
      traits,
      speakingStyle: document.getElementById('agent-profile-style').value.trim(),
      riskPreference: document.getElementById('agent-profile-risk').value,
      behaviorGuidance: document.getElementById('agent-profile-guidance').value.trim() || null,
      avatarEmoji: document.getElementById('agent-profile-avatar').value.trim() || 'AI',
      accentColor: document.getElementById('agent-profile-color').value
    }) });
    showToast('Agent 人格已保存，可在创建对局时分配');
    await loadAgentProfiles();
  } catch (error) {
    showToast(`人格保存失败：${error.message}`);
  } finally {
    button.disabled = false;
    button.textContent = '保存 Agent 人格';
  }
});

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
  button.addEventListener('click', () => { loadSavedModels(); loadAgentProfiles(); });
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
  if (/^#(?:public-game=\d+|clip=\d+:[a-z0-9-]+)$/.test(window.location.hash)) {
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
