const PERSONAS = [
  ['AGGRESSIVE_LEADER', '激进领袖', '主动制造讨论焦点并推动归票', '短句、直接点名', 'BOLD', '主动质疑'],
  ['CAUTIOUS_ANALYST', '谨慎分析者', '重视公开记录，不仓促定性', '克制、保留不确定性', 'CAUTIOUS', '历史发言与投票'],
  ['LOGIC_DETECTIVE', '逻辑侦探', '寻找声明与票型的前后矛盾', '精确引用矛盾点', 'BALANCED', '声明冲突和投票变化'],
  ['EMOTIONAL_COUNTER', '情绪反击者', '被怀疑时要求对方给出依据', '直接但不人身攻击', 'BOLD', '回应针对自己的攻击'],
  ['DISGUISE_EXPERT', '伪装专家', '用有限承诺保留判断空间', '含蓄、试探', 'BALANCED', '延后硬性站队'],
  ['MAJORITY_FOLLOWER', '多数跟随者', '关注场上共识并快速调整', '简洁复述主流理由', 'CAUTIOUS', '集中怀疑与领先意向'],
  ['HIGH_RISK_GAMBLER', '高风险赌徒', '低信息时也敢于明确判断', '果断、鲜明', 'BOLD', '低信息下选择目标'],
  ['LONG_MEMORY', '长期记忆者', '跨轮追踪未解释的矛盾', '引用轮次和旧立场', 'BALANCED', '多轮行为追踪']
];

const PLAYERS = Array.from({ length: 8 }, (_, index) => ({
  id: 101 + index,
  seatNo: index + 1,
  playerType: 'AI',
  modelConfigId: index < 4 ? 1 : null,
  modelProvider: index < 4 ? 'DEEPSEEK' : null,
  modelName: index < 4 ? 'deepseek-chat' : null,
  modelDisplayName: index < 4 ? 'DeepSeek 推理席' : null,
  localStrategy: index >= 4,
  agentProfileId: 7 + index,
  agentProfileCode: PERSONAS[index][0],
  agentProfileName: PERSONAS[index][1],
  agentDescription: PERSONAS[index][2],
  agentTraits: [PERSONAS[index][5], PERSONAS[index][4] === 'BOLD' ? '果断' : '观察'],
  agentSpeakingStyle: PERSONAS[index][3],
  agentRiskPreference: PERSONAS[index][4],
  agentReasoningPreference: PERSONAS[index][5],
  agentBuiltIn: true,
  agentAvatarEmoji: ['⚔', '◐', '⌕', '⚡', '◇', '◎', '♠', '◫'][index],
  agentAccentColor: index % 2 === 0 ? '#8B7CFF' : '#FF8844',
  displayName: ['Alpha', 'Bravo', 'Charlie', 'Delta', 'Echo', 'Foxtrot', 'Golf', 'Hotel'][index],
  status: index === 5 ? 'ELIMINATED' : 'ALIVE',
  roleCode: ['WEREWOLF', 'VILLAGER', 'SEER', 'WITCH', 'GUARD', 'VILLAGER', 'WEREWOLF', 'VILLAGER'][index],
  antidoteAvailable: index === 3,
  poisonAvailable: index === 3,
  seerChecks: index === 2 ? { 106: false } : {}
}));

const EVENTS = [
  { sequenceNo: 38, eventType: 'PLAYER_SPOKE', actorPlayerId: 101, roundNo: 2, phase: 'DAY_DISCUSSION', payload: { actorSeatNo: 1, playerId: 101, message: '3号上一轮支持5号，这一轮却准备改投5号，我希望先核对这个矛盾。', publicSpeech: '3号上一轮支持5号，这一轮却准备改投5号，我希望先核对这个矛盾。', claims: ['VILLAGER'], suspects: [3, 5], supports: [6], voteIntentSeatNo: 3, reasonTags: ['STATEMENT_CONFLICT', 'VOTE_SHIFT'], reasonSummary: '3号立场变化缺少解释', confidence: 0.76 }, createTime: '2026-07-16T10:20:00' },
  { sequenceNo: 39, eventType: 'PLAYER_SPOKE', actorPlayerId: 103, roundNo: 2, phase: 'DAY_DISCUSSION', payload: { actorSeatNo: 3, playerId: 103, message: '5号的新发言改变了我的判断，我会优先检查5号而不是继续支持。', publicSpeech: '5号的新发言改变了我的判断，我会优先检查5号而不是继续支持。', claims: ['VILLAGER'], suspects: [5, 1], supports: [2], voteIntentSeatNo: 5, reasonTags: ['NEW_INFORMATION'], reasonSummary: '根据5号新发言改变判断', confidence: 0.68 }, createTime: '2026-07-16T10:20:02' },
  { sequenceNo: 40, eventType: 'CONTROVERSY_IDENTIFIED', actorPlayerId: null, roundNo: 2, phase: 'DAY_QUESTION', payload: { controversialSeatNos: [3, 5] }, createTime: '2026-07-16T10:20:03' },
  { sequenceNo: 41, eventType: 'AI_DECISION', actorPlayerId: 108, roundNo: 2, phase: 'DAY_QUESTION', payload: { action: 'QUESTION', targetSeatNo: 3, targetPlayerId: 103, reasonSummary: '要求解释投票立场变化', reasonTags: ['VOTE_SHIFT'], confidence: 0.82, source: 'MODEL' }, createTime: '2026-07-16T10:20:04' },
  { sequenceNo: 42, eventType: 'PLAYER_QUESTIONED', actorPlayerId: 108, roundNo: 2, phase: 'DAY_QUESTION', payload: { actorPlayerId: 108, targetPlayerId: 103, actorSeatNo: 8, targetSeatNo: 3, publicSpeech: '你上一轮支持5号，为什么这一轮突然准备改投5号？', reasonTags: ['VOTE_SHIFT'], reasonSummary: '要求解释投票立场变化', confidence: 0.82 }, createTime: '2026-07-16T10:20:05' },
  { sequenceNo: 43, eventType: 'PHASE_CHANGED', actorPlayerId: null, roundNo: 2, phase: 'DAY_RESPONSE', payload: { phase: 'DAY_RESPONSE' }, createTime: '2026-07-16T10:20:06' },
  { sequenceNo: 44, eventType: 'PLAYER_VOTED', actorPlayerId: 102, roundNo: 2, phase: 'DAY_VOTE', payload: { actorSeatNo: 2, targetSeatNo: 3, playerId: 102, targetPlayerId: 103 }, createTime: '2026-07-16T10:20:07' },
  { sequenceNo: 45, eventType: 'PLAYER_VOTED', actorPlayerId: 104, roundNo: 2, phase: 'DAY_VOTE', payload: { actorSeatNo: 4, targetSeatNo: 3, playerId: 104, targetPlayerId: 103 }, createTime: '2026-07-16T10:20:08' }
];

const PROJECTION = {
  schemaVersion: 2,
  gameId: 42,
  mode: 'WEREWOLF',
  generatedThroughSequence: 45,
  commentary: [
    { itemId: 'c-g42-s38', sequenceNo: 38, endSequenceNo: 38, roundNo: 2, phase: 'DAY_DISCUSSION', category: 'ACTION', title: '1号质疑3号立场变化', narration: '1号指出3号上一轮支持5号，本轮却准备改投5号。', importance: 2, actorPlayerId: 101, actorName: 'Alpha', targetPlayerId: 103, targetName: 'Charlie', eventTypes: ['PLAYER_SPOKE'], metrics: {} },
    { itemId: 'c-g42-s40', sequenceNo: 40, endSequenceNo: 40, roundNo: 2, phase: 'DAY_QUESTION', category: 'MOMENTUM', title: '3号成为当前最受怀疑玩家', narration: '共有4名玩家将3号列入怀疑名单。', importance: 3, targetPlayerId: 103, targetName: 'Charlie', eventTypes: ['CONTROVERSY_IDENTIFIED'], metrics: { count: 4 } },
    { itemId: 'c-g42-s42', sequenceNo: 41, endSequenceNo: 42, roundNo: 2, phase: 'DAY_QUESTION', category: 'ACTION', title: '8号向3号发起质询', narration: '8号要求3号解释投票立场变化。', decisionSummary: '要求解释投票立场变化', importance: 3, actorPlayerId: 108, actorName: 'Hotel', targetPlayerId: 103, targetName: 'Charlie', eventTypes: ['AI_DECISION', 'PLAYER_QUESTIONED'], metrics: {} },
    { itemId: 'c-g42-s45', sequenceNo: 44, endSequenceNo: 45, roundNo: 2, phase: 'DAY_VOTE', category: 'ACTION', title: '3号暂时领票', narration: '2号和4号已将票投给3号，目前3号获得2票。', importance: 2, targetPlayerId: 103, targetName: 'Charlie', eventTypes: ['PLAYER_VOTED'], metrics: { votes: 2 } }
  ],
  highlights: [
    { clipId: 'g42-s41-question', gameId: 42, category: 'DIRECT_CONFLICT', title: '8号定向质询3号', narration: '围绕投票立场变化的直接冲突。', importance: 3, startSequenceNo: 41, endSequenceNo: 42, roundNo: 2, phase: 'DAY_QUESTION', actorPlayerId: 108, actorName: 'Hotel', targetPlayerId: 103, targetName: 'Charlie', eventTypes: ['PLAYER_QUESTIONED'], metrics: {} }
  ],
  werewolfSituation: {
    roundNo: 2,
    phase: 'DAY_RESPONSE',
    players: [
      { seatNo: 1, displayName: 'Alpha', claims: ['VILLAGER'], suspects: [{ seatNo: 3, displayName: 'Charlie' }, { seatNo: 5, displayName: 'Echo' }], supports: [{ seatNo: 6, displayName: 'Foxtrot' }], voteIntent: { seatNo: 3, displayName: 'Charlie' }, reasonTags: ['STATEMENT_CONFLICT', 'VOTE_SHIFT'], reasonSummary: '3号立场变化缺少解释', lastAction: 'SPEAK', updatedRound: 2 },
      { seatNo: 2, displayName: 'Bravo', claims: [], suspects: [{ seatNo: 3, displayName: 'Charlie' }], supports: [{ seatNo: 1, displayName: 'Alpha' }], voteIntent: { seatNo: 3, displayName: 'Charlie' }, reasonTags: ['MAJORITY_PRESSURE'], reasonSummary: '3号尚未解释变化', lastAction: 'SPEAK', updatedRound: 2 },
      { seatNo: 3, displayName: 'Charlie', claims: ['VILLAGER'], suspects: [{ seatNo: 5, displayName: 'Echo' }, { seatNo: 1, displayName: 'Alpha' }], supports: [{ seatNo: 2, displayName: 'Bravo' }], voteIntent: { seatNo: 5, displayName: 'Echo' }, reasonTags: ['NEW_INFORMATION'], reasonSummary: '根据5号新发言改变判断', lastAction: 'SPEAK', updatedRound: 2 },
      { seatNo: 4, displayName: 'Delta', claims: [], suspects: [{ seatNo: 3, displayName: 'Charlie' }], supports: [{ seatNo: 6, displayName: 'Foxtrot' }], voteIntent: { seatNo: 3, displayName: 'Charlie' }, reasonTags: ['VOTE_SHIFT'], reasonSummary: '优先处理未解释的票型变化', lastAction: 'SPEAK', updatedRound: 2 },
      { seatNo: 5, displayName: 'Echo', claims: ['SEER'], suspects: [{ seatNo: 3, displayName: 'Charlie' }], supports: [], voteIntent: { seatNo: 3, displayName: 'Charlie' }, reasonTags: ['CLAIM_CONFLICT'], reasonSummary: '身份声明与行为不一致', lastAction: 'SPEAK', updatedRound: 2 },
      { seatNo: 8, displayName: 'Hotel', claims: [], suspects: [{ seatNo: 3, displayName: 'Charlie' }], supports: [{ seatNo: 1, displayName: 'Alpha' }], voteIntent: { seatNo: 3, displayName: 'Charlie' }, reasonTags: ['VOTE_SHIFT'], reasonSummary: '要求解释投票立场变化', lastAction: 'QUESTION', updatedRound: 2 }
    ],
    suspicionRanking: [{ player: { seatNo: 3, displayName: 'Charlie' }, count: 4 }, { player: { seatNo: 5, displayName: 'Echo' }, count: 2 }],
    supportRanking: [{ player: { seatNo: 6, displayName: 'Foxtrot' }, count: 2 }],
    voteIntentDistribution: [{ player: { seatNo: 3, displayName: 'Charlie' }, count: 5 }, { player: { seatNo: 5, displayName: 'Echo' }, count: 1 }],
    controversialPlayers: [{ seatNo: 3, displayName: 'Charlie' }, { seatNo: 5, displayName: 'Echo' }],
    mostSuspected: { seatNo: 3, displayName: 'Charlie' },
    relationships: [
      { type: 'SUSPECT', from: { seatNo: 1, displayName: 'Alpha' }, to: { seatNo: 3, displayName: 'Charlie' } },
      { type: 'SUSPECT', from: { seatNo: 3, displayName: 'Charlie' }, to: { seatNo: 1, displayName: 'Alpha' } },
      { type: 'SUSPECT', from: { seatNo: 4, displayName: 'Delta' }, to: { seatNo: 3, displayName: 'Charlie' } },
      { type: 'SUPPORT', from: { seatNo: 1, displayName: 'Alpha' }, to: { seatNo: 6, displayName: 'Foxtrot' } },
      { type: 'SUPPORT', from: { seatNo: 8, displayName: 'Hotel' }, to: { seatNo: 1, displayName: 'Alpha' } },
      { type: 'VOTE', from: { seatNo: 2, displayName: 'Bravo' }, to: { seatNo: 3, displayName: 'Charlie' } },
      { type: 'VOTE', from: { seatNo: 4, displayName: 'Delta' }, to: { seatNo: 3, displayName: 'Charlie' } }
    ]
  }
};

function success(data) {
  return { code: '00000', msg: '成功', data };
}

async function installMockApi(page, options = {}) {
  let started = options.started ?? true;
  let activeGameId = 42;
  const finished = options.finished ?? false;
  const voteStage = options.voteStage ?? false;
  const visibleEvents = voteStage ? EVENTS : EVENTS.filter((event) => event.sequenceNo <= 43);
  const game = {
    id: activeGameId,
    name: '人格辨识度实验局',
    mode: 'WEREWOLF',
    status: finished ? 'FINISHED' : started ? 'RUNNING' : 'WAITING',
    currentPhase: finished ? 'FINISHED' : started ? (voteStage ? 'DAY_VOTE' : 'DAY_RESPONSE') : 'NIGHT_WOLF',
    currentRound: finished ? 6 : started ? 2 : 0,
    maxPlayers: 8,
    publicGame: true,
    currentActorPlayerId: started && !finished ? (voteStage ? 105 : 103) : null,
    currentActorName: started && !finished ? (voteStage ? 'Echo' : 'Charlie') : null,
    currentActionType: started && !finished ? (voteStage ? 'VOTE' : 'RESPOND') : null,
    currentActorModelProvider: started && !finished && !voteStage ? 'DEEPSEEK' : null,
    currentActorModelName: started && !finished && !voteStage ? 'deepseek-chat' : null,
    currentActorModelDisplayName: started && !voteStage ? 'DeepSeek 推理席' : null,
    currentActorLocalStrategy: started && !finished ? voteStage : false,
    currentActorPersonaName: started && !finished ? (voteStage ? '伪装专家' : '逻辑侦探') : null,
    currentActorPersonaCode: started && !finished ? (voteStage ? 'DISGUISE_EXPERT' : 'LOGIC_DETECTIVE') : null,
    updateTime: '2026-07-16T10:20:11'
  };

  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const pathname = url.pathname;
    const method = request.method();
    if (pathname.endsWith('/stream')) {
      await route.fulfill({ status: 200, contentType: 'text/event-stream', body: ': mock stream\n\n' });
      return;
    }
    let data = null;
    if (pathname === '/api/users/visitor' && method === 'POST') data = { userId: 1, nickname: '视觉测试用户', visitorToken: 'test-token', account: false };
    else if (pathname === '/api/users/me') data = { id: 1, nickname: '视觉测试用户', account: false };
    else if (pathname === '/api/games' && method === 'GET') data = options.emptyLobby ? [] : [game];
    else if (pathname === '/api/model-configs' && method === 'GET') data = [{ id: 1, name: 'DeepSeek 推理席', provider: 'DEEPSEEK', modelName: 'deepseek-chat', enabled: true }];
    else if (pathname === '/api/agent-profiles' && method === 'GET') data = PERSONAS.map((persona, index) => ({
      id: 7 + index, code: persona[0], name: persona[1], description: persona[2], traits: [persona[5]],
      speakingStyle: persona[3], riskPreference: persona[4], reasoningPreference: persona[5], builtIn: true,
      behaviorGuidance: persona[2], avatarEmoji: ['⚔', '◐', '⌕', '⚡', '◇', '◎', '♠', '◫'][index], accentColor: '#8B7CFF'
    }));
    else if (pathname === '/api/lineup-templates' && method === 'GET') data = [{ id: 3, name: '推理对抗组', mode: 'WEREWOLF', playerCount: 8, modelSeatCount: 4, personaSeatCount: 8, publicGame: true, playerNames: PLAYERS.map((player) => player.displayName) }];
    else if ((pathname === '/api/games/werewolf' || pathname === '/api/games/battle-royale') && method === 'POST') { started = false; Object.assign(game, { status: 'WAITING', currentRound: 0 }); data = game; }
    else if (/\/api\/lineup-templates\/\d+\/games/.test(pathname) && method === 'POST') { started = false; Object.assign(game, { status: 'WAITING', currentRound: 0 }); data = game; }
    else if (pathname.endsWith('/start') || pathname.endsWith('/battle-start')) { started = true; Object.assign(game, { id: activeGameId, status: 'RUNNING', currentPhase: 'NIGHT_WOLF', currentRound: 1 }); data = game; }
    else if (pathname.endsWith('/rematch') && method === 'POST') {
      activeGameId = 43;
      started = false;
      Object.assign(game, { id: activeGameId, status: 'WAITING', currentPhase: 'NIGHT_WOLF', currentRound: 0, name: `${game.name} · 重赛` });
      data = game;
    }
    else if (/\/api\/(?:public\/)?games\/\d+\/players$/.test(pathname)) data = pathname.startsWith('/api/public/')
      ? PLAYERS.map(({ modelConfigId, agentProfileId, roleCode, antidoteAvailable, poisonAvailable, seerChecks, ...safe }) => safe)
      : PLAYERS;
    else if (/\/api\/(?:public\/)?games\/\d+\/replay$/.test(pathname)) {
      data = visibleEvents.filter((event) => event.sequenceNo > Number(url.searchParams.get('afterSequence') || 0))
        .slice(0, Number(url.searchParams.get('limit') || 500));
    }
    else if (/\/api\/(?:public\/)?games\/\d+\/summary$/.test(pathname)) data = { gameId: activeGameId, status: game.status, winner: null, eventCount: visibleEvents.length, eliminationCount: 0, keyEvents: [] };
    else if (/\/api\/(?:public\/)?games\/\d+\/projection$/.test(pathname)) data = {
      ...PROJECTION,
      gameId: activeGameId,
      generatedThroughSequence: visibleEvents.at(-1)?.sequenceNo || 0,
      commentary: voteStage ? PROJECTION.commentary : PROJECTION.commentary.filter((item) => item.endSequenceNo <= 42),
      werewolfSituation: {
        ...PROJECTION.werewolfSituation,
        phase: voteStage ? 'DAY_VOTE' : 'DAY_RESPONSE',
        relationships: voteStage ? PROJECTION.werewolfSituation.relationships
          : PROJECTION.werewolfSituation.relationships.filter((relation) => relation.type !== 'VOTE')
      }
    };
    else if (/\/api\/(?:public\/)?games\/\d+$/.test(pathname)) data = game;
    else if (pathname === '/api/agent-profiles' && method === 'POST') data = { id: 9 };
    else if (pathname.startsWith('/api/lineup-templates/') && method === 'POST') data = { id: 4 };
    else data = null;
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(success(data)) });
  });
}

module.exports = { installMockApi, PLAYERS, EVENTS, PROJECTION };
