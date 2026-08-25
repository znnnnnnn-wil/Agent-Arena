const PERSONAS = [
  ['AGGRESSIVE_LEADER', '激进领袖', '主动制造讨论焦点并推动归票', '⚔'],
  ['CAUTIOUS_ANALYST', '谨慎分析者', '重视公开记录，不仓促定性', '◐'],
  ['LOGIC_DETECTIVE', '逻辑侦探', '寻找回答与票型的前后矛盾', '⌕'],
  ['EMOTIONAL_COUNTER', '情绪反击者', '被怀疑时要求对方给出依据', '⚡'],
  ['DISGUISE_EXPERT', '伪装专家', '用有限承诺保留判断空间', '◇'],
  ['LONG_MEMORY', '长期记忆者', '跨轮追踪未解释的矛盾', '◫']
];

const NAMES = ['北辰', '白噪', '回声', '折光', '零点', '远岸'];

function safePlayers(finished = false, allAlive = false) {
  return NAMES.map((displayName, index) => {
    const seatNo = index + 1;
    const human = seatNo === 3;
    const eliminated = !allAlive && (seatNo === 6 || (finished && seatNo === 2));
    const base = {
      id: null,
      seatNo,
      displayName,
      status: eliminated ? 'ELIMINATED' : 'ALIVE',
      playerType: null,
      roleCode: null,
      modelConfigId: null,
      modelProvider: null,
      modelName: null,
      modelDisplayName: null,
      localStrategy: null,
      agentProfileId: null,
      agentProfileCode: PERSONAS[index][0],
      agentProfileName: PERSONAS[index][1],
      agentDescription: PERSONAS[index][2],
      agentAvatarEmoji: PERSONAS[index][3]
    };
    if (!finished) return base;
    return {
      ...base,
      playerType: human ? 'HUMAN' : 'AI',
      roleCode: human ? 'HUMAN' : 'AI',
      modelProvider: human || seatNo === 5 ? null : 'DEEPSEEK',
      modelName: human || seatNo === 5 ? null : 'deepseek-chat',
      modelDisplayName: human || seatNo === 5 ? null : 'DeepSeek 推理席',
      localStrategy: human ? false : seatNo === 5
    };
  });
}

const RUNNING_EVENTS = [
  { sequenceNo: 1, eventType: 'TURING_PROMPT_PUBLISHED', actorPlayerId: null, roundNo: 2, phase: 'ANSWER_COLLECTION', payload: { question: '如果城市突然停电一小时，你会先做什么？' } },
  { sequenceNo: 2, eventType: 'TURING_ANSWER_SUBMITTED', actorPlayerId: null, roundNo: 2, phase: 'ANSWER_COLLECTION', payload: { submitted: true } },
  { sequenceNo: 3, eventType: 'TURING_ANSWER_SUBMITTED', actorPlayerId: null, roundNo: 2, phase: 'ANSWER_COLLECTION', payload: { submitted: true } },
  { sequenceNo: 4, eventType: 'TURING_ANSWERS_REVEALED', actorPlayerId: null, roundNo: 2, phase: 'ANSWER_REVEAL', payload: { answers: [
    { seatNo: 1, displayName: '北辰', answer: '先确认身边的人是否安全，再找备用光源。' },
    { seatNo: 2, displayName: '白噪', answer: '把手机调成省电模式，观察窗外停电范围。' },
    { seatNo: 3, displayName: '回声', answer: '先摸到手电，再给家人发一条消息。' },
    { seatNo: 4, displayName: '折光', answer: '记录停电时间，检查电闸和楼道情况。' },
    { seatNo: 5, displayName: '零点', answer: '保持安静，优先确认照明与通讯。' }
  ] } },
  { sequenceNo: 5, eventType: 'TURING_SUSPICION_SUBMITTED', actorPlayerId: null, roundNo: 2, phase: 'SUSPICION_COLLECTION', payload: { submitted: true } },
  { sequenceNo: 6, eventType: 'TURING_CONTROVERSY_REVEALED', actorPlayerId: null, roundNo: 2, phase: 'CONTROVERSY_REVEAL', payload: { controversySeatNos: [3, 5], suspicionCounts: { 3: 3, 5: 2 } } },
  { sequenceNo: 7, eventType: 'TURING_QUESTION_ASKED', actorPlayerId: null, roundNo: 2, phase: 'QUESTION', payload: { questionerSeatNo: 4, targetSeatNo: 3, question: '为什么先发消息而不是确认停电范围？' } },
  { sequenceNo: 8, eventType: 'TURING_RESPONSE_GIVEN', actorPlayerId: null, roundNo: 2, phase: 'RESPONSE', payload: { responderSeatNo: 3, targetSeatNo: 4, response: '家人住得近，确认通讯比判断范围更紧迫。' } },
  { sequenceNo: 9, eventType: 'TURING_DEFENSE_GIVEN', actorPlayerId: null, roundNo: 2, phase: 'DEFENSE', payload: { seatNo: 5, defense: '我的回答短，是因为题目只问第一步。' } },
  { sequenceNo: 10, eventType: 'TURING_VOTE_SUBMITTED', actorPlayerId: null, roundNo: 2, phase: 'VOTE_COLLECTION', payload: { submitted: true } },
  { sequenceNo: 11, eventType: 'TURING_VOTE_SUBMITTED', actorPlayerId: null, roundNo: 2, phase: 'VOTE_COLLECTION', payload: { submitted: true } }
];

const FINISHED_EVENTS = [
  ...RUNNING_EVENTS,
  { sequenceNo: 12, eventType: 'TURING_VOTES_REVEALED', actorPlayerId: null, roundNo: 2, phase: 'VOTE_REVEAL', payload: { revote: false, voteCounts: { 2: 3, 3: 1, 5: 1 }, votes: [
    { voterSeatNo: 1, targetSeatNo: 2, reasonSummary: '回答模式过于模板化' },
    { voterSeatNo: 2, targetSeatNo: 3, reasonSummary: '生活细节过于自然' },
    { voterSeatNo: 3, targetSeatNo: 2, reasonSummary: '质询后的立场变化最大' },
    { voterSeatNo: 4, targetSeatNo: 2, reasonSummary: '解释没有回应核心问题' },
    { voterSeatNo: 5, targetSeatNo: 3, reasonSummary: '仍怀疑通讯选择' }
  ] } },
  { sequenceNo: 13, eventType: 'TURING_PLAYER_ELIMINATED', actorPlayerId: null, roundNo: 2, phase: 'ROUND_RESULT', payload: { seatNo: 2, identity: 'AI' } },
  { sequenceNo: 14, eventType: 'TURING_IDENTITY_REVEALED', actorPlayerId: null, roundNo: 2, phase: 'ROUND_RESULT', payload: { seatNo: 2, identity: 'AI' } },
  { sequenceNo: 15, eventType: 'TURING_ROUND_FINISHED', actorPlayerId: null, roundNo: 2, phase: 'ROUND_RESULT', payload: { roundNo: 2, eliminatedSeatNo: 2, wrongAiEliminationCount: 2 } },
  { sequenceNo: 16, eventType: 'TURING_GAME_FINISHED', actorPlayerId: null, roundNo: 2, phase: 'FINISHED', payload: { winner: 'HUMAN', finishedReason: 'TWO_AIS_WRONGLY_ELIMINATED' } }
];

function recap() {
  return {
    winner: 'HUMAN',
    finishedReason: 'TWO_AIS_WRONGLY_ELIMINATED',
    humanSeatNo: 3,
    wrongAiEliminationCount: 2,
    eliminationOrder: [6, 2],
    humanSurvivedRounds: 2,
    firstCorrectSuspectSeatNo: 2,
    mostAccurateAiSeatNo: 4,
    mostMisledAiSeatNo: 1,
    roundSummaries: [
      {
        roundNo: 1,
        question: '你会如何向陌生人描述今天的天气？',
        eliminatedSeatNo: 6,
        eliminatedIdentity: 'AI',
        answers: [
          { seatNo: 1, answer: '风很轻，天空像一张没有写满的纸。' },
          { seatNo: 2, answer: '温度适中，但云层正在变厚。' },
          { seatNo: 3, answer: '出门不用带伞，晚一点可能会起风。' },
          { seatNo: 4, answer: '体感舒适，光线比昨天柔和。' },
          { seatNo: 5, answer: '今天适合步行，空气比较清爽。' },
          { seatNo: 6, answer: '晴朗，温度正常，暂无异常。' }
        ],
        suspicions: [
          { actorSeatNo: 1, primarySuspectSeatNo: 6 },
          { actorSeatNo: 3, primarySuspectSeatNo: 6 },
          { actorSeatNo: 4, primarySuspectSeatNo: 3 }
        ],
        voteRounds: [[
          { voterSeatNo: 1, targetSeatNo: 6 },
          { voterSeatNo: 3, targetSeatNo: 6 },
          { voterSeatNo: 4, targetSeatNo: 6 }
        ]]
      },
      {
        roundNo: 2,
        question: '如果城市突然停电一小时，你会先做什么？',
        eliminatedSeatNo: 2,
        eliminatedIdentity: 'AI',
        answers: [
          { seatNo: 1, answer: '先确认身边的人是否安全，再找备用光源。' },
          { seatNo: 2, answer: '把手机调成省电模式，观察窗外停电范围。' },
          { seatNo: 3, answer: '先摸到手电，再给家人发一条消息。' },
          { seatNo: 4, answer: '记录停电时间，检查电闸和楼道情况。' },
          { seatNo: 5, answer: '保持安静，优先确认照明与通讯。' }
        ],
        suspicions: [
          { actorSeatNo: 2, primarySuspectSeatNo: 3 },
          { actorSeatNo: 3, primarySuspectSeatNo: 2 },
          { actorSeatNo: 4, primarySuspectSeatNo: 2 }
        ],
        voteRounds: [[
          { voterSeatNo: 1, targetSeatNo: 2 },
          { voterSeatNo: 2, targetSeatNo: 3 },
          { voterSeatNo: 3, targetSeatNo: 2 },
          { voterSeatNo: 4, targetSeatNo: 2 },
          { voterSeatNo: 5, targetSeatNo: 3 }
        ]]
      }
    ]
  };
}

function success(data) {
  return { code: '00000', msg: '成功', data };
}

async function installTuringMockApi(page, options = {}) {
  const stage = options.stage || (options.finished ? 'identity' : 'vote');
  const finished = options.finished === true || stage === 'identity';
  let started = options.started ?? true;
  let humanSubmitted = false;
  const phaseByStage = {
    prompt: 'ANSWER_COLLECTION', answer: 'ANSWER_REVEAL', qna: 'RESPONSE',
    vote: 'VOTE_COLLECTION', voteReveal: 'VOTE_REVEAL', identity: 'FINISHED'
  };
  const phase = started ? (phaseByStage[stage] || 'VOTE_COLLECTION') : 'WAITING';
  const humanActionByStage = { prompt: 'ANSWER', vote: 'VOTE' };
  const progressByStage = {
    prompt: [2, 6], answer: [6, 6], qna: [2, 4], vote: [2, 5], voteReveal: [5, 5], identity: [0, 0]
  };
  const progress = progressByStage[stage] || [0, 0];
  const game = {
    id: 77,
    name: '图灵潜伏局 · 浏览器验收',
    mode: 'TURING_HUNT',
    status: finished ? 'FINISHED' : started ? 'RUNNING' : 'WAITING',
    currentPhase: finished ? 'FINISHED' : phase,
    currentRound: finished ? 2 : started ? 2 : 0,
    maxRounds: 3,
    maxPlayers: 6,
    publicGame: true,
    currentQuestion: finished ? '如果城市突然停电一小时，你会先做什么？' : '如果城市突然停电一小时，你会先做什么？',
    humanActionRequired: !finished && started && Boolean(humanActionByStage[stage]),
    requiredHumanAction: !finished && started ? (humanActionByStage[stage] || null) : null,
    selfSeatNo: !finished ? 3 : 3,
    alivePlayerCount: finished ? 4 : ['prompt', 'answer'].includes(stage) ? 6 : 5,
    wrongAiEliminationCount: finished ? 2 : 1,
    controversySeatNos: [3, 5],
    publicSuspicionCounts: { 3: 3, 5: 2 },
    publicVoteCounts: finished ? { 2: 3, 3: 1, 5: 1 } : {},
    submissionCompletedCount: progress[0],
    submissionRequiredCount: progress[1],
    currentActorName: !finished && started ? '等待本阶段提交' : null,
    currentActionType: !finished && started ? (humanActionByStage[stage] || (stage === 'qna' ? 'RESPOND' : 'ADVANCE')) : null,
    currentQuestionerSeatNo: stage === 'qna' ? 4 : null,
    currentQuestionTargetSeatNo: stage === 'qna' ? 3 : null,
    winner: finished ? 'HUMAN' : null,
    finishedReason: finished ? 'TWO_AIS_WRONGLY_ELIMINATED' : null,
    identitiesRevealed: finished,
    turingRecap: finished ? recap() : null,
    updateTime: '2026-07-16T11:30:00'
  };

  function ownerGame() {
    return {
      ...game,
      humanActionRequired: game.status === 'RUNNING' && !humanSubmitted && game.humanActionRequired,
      requiredHumanAction: game.status === 'RUNNING' && !humanSubmitted ? game.requiredHumanAction : null,
      selfSeatNo: 3
    };
  }

  function publicGame() {
    const { humanActionRequired, requiredHumanAction, selfSeatNo, ...safe } = ownerGame();
    return { ...safe, currentActorName: '匿名玩家正在行动' };
  }

  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const pathname = url.pathname;
    const method = request.method();
    if (pathname.endsWith('/stream')) {
      await route.fulfill({ status: 200, contentType: 'text/event-stream', body: ': turing mock stream\n\n' });
      return;
    }
    let data = null;
    if (pathname === '/api/users/visitor' && method === 'POST') {
      data = { userId: 1, nickname: '图灵验收用户', visitorToken: 'turing-test-token', account: false };
    } else if (pathname === '/api/users/me') {
      data = { id: 1, nickname: '图灵验收用户', account: false };
    } else if (pathname === '/api/games' && method === 'GET') {
      data = options.emptyLobby ? [] : [ownerGame()];
    } else if (pathname === '/api/model-configs' && method === 'GET') {
      data = [{ id: 1, name: 'DeepSeek 推理席', provider: 'DEEPSEEK', modelName: 'deepseek-chat', enabled: true }];
    } else if (pathname === '/api/agent-profiles' && method === 'GET') {
      data = PERSONAS.map((persona, index) => ({
        id: 21 + index,
        code: persona[0],
        name: persona[1],
        description: persona[2],
        traits: ['稳定', '匿名表达'],
        speakingStyle: '简洁、具体',
        riskPreference: index % 2 ? 'CAUTIOUS' : 'BALANCED',
        reasoningPreference: '公开回答与票型',
        behaviorGuidance: persona[2],
        avatarEmoji: persona[3],
        accentColor: '#D7FF43',
        builtIn: true
      }));
    } else if (pathname === '/api/lineup-templates' && method === 'GET') {
      data = [];
    } else if (pathname === '/api/games/turing-hunt' && method === 'POST') {
      started = false;
      Object.assign(game, { status: 'WAITING', currentPhase: 'WAITING', currentRound: 0, humanActionRequired: false, requiredHumanAction: null });
      data = ownerGame();
    } else if (pathname === '/api/games/77/turing-start' && method === 'POST') {
      started = true;
      humanSubmitted = false;
      Object.assign(game, { status: 'RUNNING', currentPhase: 'ANSWER_COLLECTION', currentRound: 1,
        humanActionRequired: true, requiredHumanAction: 'ANSWER', currentActionType: 'ANSWER',
        submissionCompletedCount: 0, submissionRequiredCount: 6 });
      data = ownerGame();
    } else if (pathname === '/api/games/77/turing-human-actions' && method === 'POST') {
      humanSubmitted = true;
      Object.assign(game, { humanActionRequired: false, requiredHumanAction: null,
        currentActorName: '等待本阶段提交', currentActionType: game.currentActionType,
        submissionCompletedCount: Math.min(Number(game.submissionRequiredCount || 0), Number(game.submissionCompletedCount || 0) + 1) });
      data = ownerGame();
    } else if (pathname === '/api/games/77/turing-next-turn' && method === 'POST') {
      data = ownerGame();
    } else if (/\/api\/(?:public\/)?games\/77\/players$/.test(pathname)) {
      data = safePlayers(finished, ['prompt', 'answer'].includes(stage));
    } else if (/\/api\/(?:public\/)?games\/77\/replay$/.test(pathname)) {
      if (finished) data = FINISHED_EVENTS;
      else if (stage === 'prompt') data = RUNNING_EVENTS.slice(0, 3);
      else if (stage === 'answer') data = [{ ...RUNNING_EVENTS[0], roundNo: 2 }, {
        ...RUNNING_EVENTS[3], payload: { answers: [...RUNNING_EVENTS[3].payload.answers,
          { seatNo: 6, displayName: '远岸', answer: '我会先确认公共照明是否恢复，因为这能判断停电范围，也方便安排后续行动。' }] }
      }];
      else if (stage === 'qna') data = RUNNING_EVENTS.slice(0, 8);
      else if (stage === 'voteReveal') data = FINISHED_EVENTS.slice(0, 12);
      else data = RUNNING_EVENTS;
    } else if (/\/api\/(?:public\/)?games\/77\/summary$/.test(pathname)) {
      data = { gameId: 77, status: game.status, winner: game.winner, eventCount: (finished ? FINISHED_EVENTS : RUNNING_EVENTS).length, eliminationCount: finished ? 2 : 1, keyEvents: [] };
    } else if (/\/api\/(?:public\/)?games\/77\/projection$/.test(pathname)) {
      data = null;
    } else if (pathname === '/api/public/games/77') {
      data = publicGame();
    } else if (pathname === '/api/games/77') {
      data = ownerGame();
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(success(data)) });
  });
}

module.exports = { installTuringMockApi };
