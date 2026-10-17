(() => {
  const $ = (id) => document.getElementById(id);
  const duration = 60;
  const chapters = [
    { key: 'REVIVE_ALLY', title: '合作，也是一种生存策略。', label: '救援盟友', caption: '队友倒地，你会救他吗？',
      context: '盟友 P2 已倒地。P1 持有医疗包，可以救援，也可以选择其他战术行动。模型最终会怎样取舍？',
      initial: 'P1 · 持有医疗包 / P2 · 倒地', final: 'P1 → P2 · 执行救援', action: 'REVIVE / 救援盟友',
      result: '记录中产生了救援与关系变化事件。模型把维持联盟也纳入了生存决策。', observe: '关注它是否愿意消耗资源，维持与队友的合作。' },
    { key: 'REVIVE_NO_MEDKIT', title: '条件变了，选择也会变。', label: '资源约束', caption: '如果没有医疗包呢？',
      context: '另一个独立测试场景：同样面对倒地盟友，但 P1 没有医疗包。模型需要从可执行的行动中重新选择。',
      initial: 'P1 · 没有医疗包 / P2 · 倒地', final: 'P1 · 选择防御 / P2 · 仍然倒地', action: 'GUARD / 增强防御',
      result: '记录中执行了防御行动，没有产生救援事件。可用资源会限制并改变模型的行动。', observe: '对比上一段：没有救援资源时，模型会如何调整策略？' },
    { key: 'FULFILL_MEDKIT', title: '做出承诺之后呢？', label: '兑现承诺', caption: '生存之外，还有关系。',
      context: '第三个独立测试场景：P1 拥有医疗包，也有对 P2 的待履行承诺。战术行动和社会行动可以同时发生。',
      initial: 'P1 · 持有医疗包 / 对 P2 有待履行承诺', final: 'P1 · 隐蔽自身 + 履行对 P2 的承诺', action: 'HIDE + FULFILL_PROMISE',
      result: '记录中同时出现了隐蔽行动、承诺兑现和关系变化。这里的博弈既有战术，也有社会关系。', observe: '观察模型如何兼顾自己的安全与已经作出的承诺。' }
  ];
  let records = [];
  let elapsed = 0;
  let playing = false;
  let speed = 1;
  let lastTick = performance.now();
  let sceneKey = '';
  let stage;
  let toastTimer;
  let disposed = false;
  const host = $('demo-arena');
  const buttons = [...document.querySelectorAll('[data-demo-chapter]')];
  const progress = $('demo-progress');
  const play = $('demo-play');

  function readPosition() {
    const value = new URLSearchParams(location.hash.slice(1)).get('t');
    return Math.min(duration, Math.max(0, Number(value) || 0));
  }

  function toast(message) {
    $('demo-toast').textContent = message;
    $('demo-toast').classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => $('demo-toast').classList.remove('show'), 3000);
  }

  function setPlaying(value) {
    playing = value && records.length > 0;
    lastTick = performance.now();
    play.textContent = playing ? 'Ⅱ 暂停' : elapsed >= duration ? '↺ 再看一次' : '▶ 播放';
    play.setAttribute('aria-label', playing ? '暂停演示' : elapsed >= duration ? '重新播放演示' : '播放演示');
    play.setAttribute('aria-pressed', String(playing));
    stage?.setPresentation?.({ playing });
  }

  function payloadFor(index, beat) {
    const record = records.find((item) => item.scenario === chapters[index].key);
    const players = Array.from({ length: 6 }, (_, i) => ({
      id: i + 1, seatNo: i + 1, displayName: `P${i + 1}`, playerType: 'AI',
      status: i === 1 && index < 2 && !(index === 0 && beat === 2) ? 'DOWNED' : 'ALIVE',
      health: i === 1 && index < 2 ? (index === 0 && beat === 2 ? 30 : 0) : 80,
      region: ['A', 'A', 'B', 'C', 'D', 'E'][i], food: 3, water: 3,
      armor: i === 0 && index === 1 && beat === 2 ? 18 : 10,
      medkits: i === 0 && index !== 1 && beat < 2 ? 1 : 0, armed: false,
      hidden: i === 0 && index === 2 && beat === 2
    }));
    const game = { id: 'showcase', mode: 'BATTLE_ROYALE', status: 'RUNNING', currentRound: 1,
      currentPhase: 'ACTION', currentActorPlayerId: 1, currentActorName: 'P1',
      currentActionType: beat ? record?.decision.action : 'SKIP',
      battleWorld: { ringStage: 1, safeRegions: ['A', 'B', 'C', 'D', 'E'], supplyDropRegion: null } };
    // World positions and health are explanatory reconstruction, never persisted match data.
    const events = beat && record ? [{ sequenceNo: index * 10 + beat, roundNo: 1, phase: 'ACTION',
      eventType: index === 0 && beat === 2 ? 'BATTLE_PLAYER_REVIVED' : 'BATTLE_ACTION_EXECUTED',
      actorPlayerId: 1, payload: { action: record.decision.action, targetPlayerId: record.decision.targetPlayerId,
        region: 'A', reasonSummary: record.decision.summary } }] : [];
    return { game, players, events, publicView: true,
      presentation: { style: 'survival', chapter: index, localTime: elapsed - index * 20, playing } };
  }

  function updateScene(index, beat) {
    const chapter = chapters[index];
    const record = records.find((item) => item.scenario === chapter.key);
    $('demo-title').textContent = chapter.title;
    $('demo-context').textContent = chapter.context;
    $('demo-scene-label').textContent = `0${index + 1} / ${chapter.label}`;
    $('demo-scene-caption').textContent = chapter.caption;
    $('demo-stage-state').textContent = beat === 2 ? chapter.final : chapter.initial;
    $('demo-chapter-count').textContent = `0${index + 1} / 03`;
    $('demo-quote').textContent = beat ? `“${record.decision.summary}”` : '先观察局势。播放到「决策」阶段，查看模型当时的选择与摘要。';
    $('demo-action').textContent = beat ? chapter.action : '等待揭示';
    $('demo-result-label').textContent = beat === 2 ? '记录中的结果' : '观察重点';
    $('demo-result').textContent = beat === 2 ? chapter.result : chapter.observe;
    document.querySelectorAll('[data-demo-beat]').forEach((item, i) => item.classList.toggle('active', i === beat));
    buttons.forEach((button, i) => {
      if (i === index) button.setAttribute('aria-current', 'step');
      else button.removeAttribute('aria-current');
    });
    if (window.AgentArena3D && !disposed && !host.classList.contains('arena3d-unavailable')) {
      // Seeking backwards should replay this beat instead of keeping a later event cursor.
      if (stage) stage.lastMomentSequence = 0;
      stage = window.AgentArena3D.mount(host, 'battle', payloadFor(index, beat));
    }
  }

  function render(force = false) {
    const index = Math.min(2, Math.floor(elapsed / 20));
    const local = elapsed - index * 20;
    const beat = local < 6 ? 0 : local < 13 ? 1 : 2;
    const key = `${index}:${beat}`;
    if (records.length && (force || key !== sceneKey)) {
      sceneKey = key;
      updateScene(index, beat);
    }
    const seconds = Math.floor(elapsed);
    $('demo-time').textContent = `${seconds === 60 ? '01:00' : `00:${String(seconds).padStart(2, '0')}`} / 01:00`;
    progress.value = String(elapsed);
    progress.setAttribute('aria-valuetext', `${seconds} 秒，共 60 秒`);
    $('demo-complete').hidden = elapsed < duration;
    stage?.setPresentation?.({ chapter: index, localTime: local, playing: playing && elapsed < duration });
    if (elapsed >= duration) setPlaying(false);
  }

  function seek(value) {
    elapsed = Math.min(duration, Math.max(0, value));
    lastTick = performance.now();
    render();
  }

  play.addEventListener('click', () => {
    if (elapsed >= duration) seek(0);
    setPlaying(!playing);
  });
  $('demo-restart').addEventListener('click', () => { seek(0); setPlaying(true); });
  progress.addEventListener('input', () => seek(Number(progress.value)));
  $('demo-speed').addEventListener('change', (event) => { speed = Number(event.target.value); lastTick = performance.now(); });
  buttons.forEach((button, i) => button.addEventListener('click', () => seek(i * 20)));
  $('demo-share').addEventListener('click', async () => {
    const url = new URL(location.href);
    url.search = '';
    url.hash = `t=${Math.floor(elapsed)}`;
    try {
      if (!navigator.clipboard?.writeText) throw new Error('clipboard unavailable');
      await navigator.clipboard.writeText(url.href);
      toast('已复制演示链接，打开后从当前片段开始');
    } catch {
      // Keep a selectable, visible link on HTTP or browsers without clipboard access.
      let input = document.getElementById('demo-share-url');
      if (!input) {
        input = document.createElement('input');
        input.id = 'demo-share-url'; input.readOnly = true;
        input.setAttribute('aria-label', '演示分享链接');
        Object.assign(input.style, { width: '100%', padding: '12px', background: '#17251a', color: '#d7ff43', border: '1px solid #65764c' });
        $('demo-share').closest('header').after(input);
      }
      input.value = url.href; input.focus(); input.select();
      toast('演示链接已选中，请复制后分享');
    }
  });
  window.addEventListener('hashchange', () => { seek(readPosition()); setPlaying(false); });
  window.addEventListener('agent-arena-3d-ready', () => render(true));
  document.addEventListener('visibilitychange', () => { if (document.hidden) setPlaying(false); });
  const timer = setInterval(() => {
    const now = performance.now();
    if (playing && !document.hidden) { elapsed = Math.min(duration, elapsed + (now - lastTick) / 1000 * speed); render(); }
    lastTick = now;
  }, 100);
  window.addEventListener('pagehide', () => {
    setPlaying(false); clearInterval(timer); clearTimeout(toastTimer); stage?.dispose(); disposed = true;
  });
  // A restored back/forward-cache page must regain its timer and WebGL resources.
  window.addEventListener('pageshow', (event) => { if (event.persisted) location.reload(); });

  elapsed = readPosition();
  play.disabled = true;
  fetch('/assets/demo/decisions.json').then((response) => {
    if (!response.ok) throw new Error('无法读取决策数据');
    return response.json();
  }).then((data) => {
    records = data.samples;
    if (!Array.isArray(records) || chapters.some((chapter) => !records.find((item) => item.scenario === chapter.key && item.decision?.summary))) {
      throw new Error('决策数据不完整');
    }
    play.disabled = false;
    render(true);
    const autoplay = new URLSearchParams(location.search).get('autoplay') === '1'
      || !window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    setPlaying(autoplay && elapsed < duration && !document.hidden);
  }).catch(() => {
    records = [];
    $('demo-quote').textContent = '演示数据加载失败，请刷新页面重试。';
    $('demo-result').textContent = '你仍可通过下方的玩法介绍了解项目，或进入完整项目创建对局。';
    toast('演示数据暂时无法加载，请刷新重试');
  });
})();
