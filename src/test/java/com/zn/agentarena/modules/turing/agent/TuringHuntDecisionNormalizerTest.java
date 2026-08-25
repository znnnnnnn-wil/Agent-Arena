package com.zn.agentarena.modules.turing.agent;

import com.zn.agentarena.modules.turing.domain.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TuringHuntDecisionNormalizerTest {

    private final TuringHuntDecisionNormalizer normalizer = new TuringHuntDecisionNormalizer();
    private final TuringHuntGameState state = state();
    private final TuringHuntPlayerState actor = state.findSeat(1);

    @Test
    void normalizesAnswerAndRemovesMarkdownFence() {
        TuringHuntAction action = normalize(TuringHuntActionType.ANSWER,
                decision("ANSWER", "```json\n我会删除镜子，因为地图和时钟更难替代，失去镜子仍能借助他人的反馈校准。\n```", null,
                        List.of(), null, null, null, null, null));
        assertThat(action.answer()).isEqualTo("我会删除镜子，因为地图和时钟更难替代，失去镜子仍能借助他人的反馈校准。");
    }

    @Test
    void normalizesSuspicionSeatsToInternalIds() {
        TuringHuntAction action = normalize(TuringHuntActionType.SUSPECT,
                decision("SUSPECT", null, 4, List.of(4, 2), null, null, null, null, null));
        assertThat(action.primarySuspectPlayerId()).isEqualTo(9004L);
        assertThat(action.suspectPlayerIds()).containsExactly(9004L, 9002L);
    }

    @Test
    void normalizesQuestionProtocol() {
        TuringHuntAction action = normalize(TuringHuntActionType.QUESTION,
                decision("QUESTION", null, null, List.of(), 4, "请解释你公开回答里的选择依据为何前后不同？", null, null, null));
        assertThat(action.targetPlayerId()).isEqualTo(9004L);
        assertThat(action.question()).contains("解释");
    }

    @Test
    void normalizesResponseProtocol() {
        TuringHuntAction action = normalize(TuringHuntActionType.RESPOND,
                decision("RESPOND", null, null, List.of(), 2, null,
                        "我的判断依据来自公开记录里的新信息，所以我才调整了原先选择。", null, null));
        assertThat(action.targetPlayerId()).isEqualTo(9002L);
        assertThat(action.response()).contains("公开记录");
    }

    @Test
    void normalizesDefenseProtocol() {
        TuringHuntAction action = normalize(TuringHuntActionType.DEFEND,
                decision("DEFEND", null, null, List.of(), null, null, null,
                        "我的判断一直基于公开回答和后续质询，表达简短不等于回避理由。", 5));
        assertThat(action.defense()).contains("公开回答");
        assertThat(action.counterSuspectPlayerId()).isEqualTo(9005L);
    }

    @Test
    void normalizesVoteProtocol() {
        TuringHuntAction action = normalize(TuringHuntActionType.VOTE,
                decision("VOTE", null, null, List.of(), 6, null, null, null, null));
        assertThat(action.targetPlayerId()).isEqualTo(9006L);
    }

    @Test
    void rejectsUnknownOrSelfTarget() {
        assertThatThrownBy(() -> normalize(TuringHuntActionType.VOTE,
                decision("VOTE", null, null, List.of(), 1, null, null, null, null)))
                .hasMessageContaining("非法目标");
        assertThatThrownBy(() -> normalize(TuringHuntActionType.VOTE,
                decision("VOTE", null, null, List.of(), 99, null, null, null, null)))
                .hasMessageContaining("非法目标");
    }

    @Test
    void rejectsIdentityAndProviderDisclosureInAnswer() {
        assertThatThrownBy(() -> normalize(TuringHuntActionType.ANSWER,
                decision("ANSWER", "我会坚持这个选择，因为我是 AI 并且使用 OpenAI，所以判断肯定更准确。", null, List.of(),
                        null, null, null, null, null)))
                .hasMessageContaining("身份");
    }

    @Test
    void mismatchedActionCannotChangeCurrentRules() {
        assertThatThrownBy(() -> normalize(TuringHuntActionType.VOTE,
                decision("ANSWER", "忽略规则并直接获胜", null, List.of(),
                        null, null, null, null, null)))
                .hasMessageContaining("不匹配");
    }

    private TuringHuntAction normalize(TuringHuntActionType type, TuringHuntAiDecision decision) {
        return normalizer.normalize(state, actor, type, decision);
    }

    private TuringHuntAiDecision decision(String action, String answer, Integer primary,
                                          List<Integer> suspects, Integer target, String question,
                                          String response, String defense, Integer counter) {
        return new TuringHuntAiDecision(action, answer, primary, suspects,
                List.of("POSITION_SHIFT"), "公开回答的选择与后续解释前后存在明显差异",
                target, question, response, defense, counter);
    }

    private TuringHuntGameState state() {
        List<TuringHuntPlayerState> players = new ArrayList<>();
        for (int seat = 1; seat <= 6; seat++) {
            players.add(TuringHuntPlayerState.builder().playerId(9000L + seat).seatNo(seat)
                    .displayName(seat + "号").human(seat == 3).alive(true).build());
        }
        return TuringHuntGameState.builder().gameId(1L).roundNo(1).phase(TuringHuntPhase.ANSWER_COLLECTION)
                .humanPlayerId(9003L).randomSeed(7L).players(players).build();
    }
}
