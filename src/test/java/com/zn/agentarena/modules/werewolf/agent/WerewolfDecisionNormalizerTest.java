package com.zn.agentarena.modules.werewolf.agent;

import com.zn.agentarena.modules.werewolf.domain.WerewolfAction;
import com.zn.agentarena.modules.werewolf.domain.WerewolfActionType;
import com.zn.agentarena.modules.werewolf.domain.WerewolfGameState;
import com.zn.agentarena.modules.werewolf.domain.WerewolfPhase;
import com.zn.agentarena.modules.werewolf.domain.WerewolfPlayerState;
import com.zn.agentarena.modules.werewolf.domain.WerewolfRole;
import com.zn.agentarena.modules.werewolf.engine.WerewolfGameEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("狼人杀模型协议规范化测试")
class WerewolfDecisionNormalizerTest {
    private final WerewolfDecisionNormalizer normalizer = new WerewolfDecisionNormalizer();

    @Test
    @DisplayName("辅助关系中的非法座位应删除而合法值保留并限制数量")
    void invalidAuxiliarySeatsAreDroppedIndividually() {
        WerewolfGameState state = state();
        WerewolfPlayerState actor = state.findPlayer(244L);
        WerewolfAiDecision decision = decision(3, null, "发言", List.of(3, 99, 6, 4, 5),
                List.of(1, 6, 2), 99);

        var normalized = normalizer.normalize(state, actor, decision, WerewolfActionType.SPEAK);

        assertEquals(List.of(3, 4, 5), normalized.suspects());
        assertEquals(List.of(1, 2), normalized.supports());
        assertNull(normalized.voteIntentSeatNo());
    }

    @Test
    @DisplayName("已死亡核心目标会在进入规则引擎时被拒绝")
    void deadCoreTargetDoesNotPassEngineValidation() {
        WerewolfGameState state = state();
        state.findPlayer(241L).setAlive(false);
        WerewolfPlayerState actor = state.findPlayer(244L);
        var normalized = normalizer.normalize(state, actor, decision(3, null, "", List.of(), List.of(), null),
                WerewolfActionType.VOTE);

        assertEquals(241L, normalized.targetPlayerId());
        assertFalse(new WerewolfGameEngine().validate(state,
                new WerewolfAction(actor.getPlayerId(), WerewolfActionType.VOTE,
                        normalized.targetPlayerId(), null)).valid());
    }

    @Test
    @DisplayName("公开发言和摘要超长时应按 Unicode 字符安全截断")
    void longSpeechAndSummaryAreSafelyLimited() {
        WerewolfGameState state = state();
        String speech = "疑".repeat(220) + "🎮";
        String summary = "理由".repeat(50);
        WerewolfAiDecision decision = new WerewolfAiDecision("VILLAGER", "SPEAK", null, null,
                speech, null, List.of(), List.of(), List.of(), null, List.of(), summary, 76D);

        var normalized = normalizer.normalize(state, state.findPlayer(244L), decision, WerewolfActionType.SPEAK);

        assertEquals(WerewolfDecisionNormalizer.SPEECH_MAX_LENGTH,
                normalized.publicSpeech().codePointCount(0, normalized.publicSpeech().length()));
        assertEquals(WerewolfDecisionNormalizer.REASON_SUMMARY_MAX_LENGTH,
                normalized.reasonSummary().codePointCount(0, normalized.reasonSummary().length()));
        assertEquals(0.76D, normalized.confidence(), 0.0001);
    }

    @Test
    @DisplayName("质询、回应和最终陈述分别使用阶段专属长度上限")
    void interactionSpeechUsesPhaseSpecificLimits() {
        WerewolfGameState state = state();
        WerewolfPlayerState actor = state.findPlayer(244L);
        String longSpeech = "证".repeat(240);
        WerewolfAiDecision decision = new WerewolfAiDecision("VILLAGER", "QUESTION", 3, null,
                longSpeech, null, List.of(), List.of(3), List.of(), 3,
                List.of("PUBLIC_EVIDENCE"), "公开依据", 0.7D);

        String question = normalizer.normalize(state, actor, decision, WerewolfActionType.QUESTION).publicSpeech();
        String response = normalizer.normalize(state, actor, decision, WerewolfActionType.RESPOND).publicSpeech();
        String finalStatement = normalizer.normalize(state, actor, decision, WerewolfActionType.FINAL_STATEMENT).publicSpeech();
        assertEquals(60, question.codePointCount(0, question.length()));
        assertEquals(120, response.codePointCount(0, response.length()));
        assertEquals(100, finalStatement.codePointCount(0, finalStatement.length()));
    }

    private WerewolfAiDecision decision(Integer targetSeatNo, Long legacyId, String speech,
                                         List<Integer> suspects, List<Integer> supports, Integer voteIntent) {
        return new WerewolfAiDecision("VILLAGER", "VOTE", targetSeatNo, legacyId, speech, null,
                List.of("VILLAGER"), suspects, supports, voteIntent,
                List.of("vote-shift", "", "new information", "extra"), "公开依据", 0.7D);
    }

    private WerewolfGameState state() {
        List<WerewolfPlayerState> players = new ArrayList<>();
        WerewolfRole[] roles = {WerewolfRole.SEER, WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF,
                WerewolfRole.VILLAGER, WerewolfRole.GUARD, WerewolfRole.VILLAGER,
                WerewolfRole.WITCH, WerewolfRole.VILLAGER};
        for (int seat = 1; seat <= 8; seat++) {
            players.add(WerewolfPlayerState.builder().playerId(238L + seat).seatNo(seat)
                    .displayName(seat + "号玩家").role(roles[seat - 1]).alive(true).build());
        }
        return WerewolfGameState.builder().gameId(31L).roundNo(1).phase(WerewolfPhase.DAY_VOTE)
                .players(players).votes(new LinkedHashMap<>()).build();
    }
}
