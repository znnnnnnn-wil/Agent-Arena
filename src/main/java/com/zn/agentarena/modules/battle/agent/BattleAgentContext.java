package com.zn.agentarena.modules.battle.agent;

import com.zn.agentarena.modules.agent.domain.AgentPersonaSnapshot;
import com.zn.agentarena.modules.battle.communication.BattleMessage;
import com.zn.agentarena.modules.battle.memory.BattleMemoryEntry;
import com.zn.agentarena.modules.battle.perception.BattleAgentObservation;
import com.zn.agentarena.modules.battle.social.BattleRelationshipState;
import com.zn.agentarena.modules.battle.social.BattleSocialContext;

import java.util.List;
import java.util.Map;

/** 已完成知识边界过滤、可以安全交给 Prompt 构建器的上下文 DTO。 */
public record BattleAgentContext(
        AgentPersonaSnapshot persona,
        BattleAgentObservation observation,
        List<BattleMemoryEntry> memories,
        Map<Long, BattleRelationshipState> relationships,
        List<BattleMessage> receivedMessages,
        BattleSocialContext social,
        List<String> gameRules
) {
}
