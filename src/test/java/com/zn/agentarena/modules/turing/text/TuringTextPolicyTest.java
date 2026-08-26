package com.zn.agentarena.modules.turing.text;

import com.zn.agentarena.modules.turing.domain.TuringHuntActionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TuringTextPolicyTest {

    private final TuringTextPolicy policy = TuringTextPolicy.INSTANCE;

    @Test
    void rulesAreTheRequestedUnicodeRanges() {
        assertRule(TuringHuntActionType.ANSWER, 30, 90, 2);
        assertRule(TuringHuntActionType.SUSPECT, 15, 50, 2);
        assertRule(TuringHuntActionType.QUESTION, 12, 45, 1);
        assertRule(TuringHuntActionType.RESPOND, 20, 70, 2);
        assertRule(TuringHuntActionType.DEFEND, 25, 80, 2);
        assertRule(TuringHuntActionType.VOTE, 12, 40, 1);
    }

    @Test
    void codePointCountTreatsEmojiAsOneCharacter() {
        assertThat(policy.codePointCount("甲😀乙")).isEqualTo(3);
    }

    @Test
    void deterministicNormalizationRemovesFormattingButPreservesNegation() {
        String normalized = policy.normalize("```markdown\n## 回答：\n- 我不会删除地图!!\n- 因为它仍能帮助复核??\n```");

        assertThat(normalized).isEqualTo("我不会删除地图！；因为它仍能帮助复核？");
        assertThat(normalized).contains("不会").doesNotContain("```", "##", "- ");
    }

    @Test
    void tooShortHumanTextGetsFriendlyRemainingCount() {
        TuringTextPolicy.TextValidation result = policy.validate(TuringHuntActionType.ANSWER,
                "我选择保留地图，因为更可靠。");

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("还需").contains("字");
    }

    @Test
    void rejectsVagueSuspicionAndStockResponse() {
        assertThat(policy.validate(TuringHuntActionType.SUSPECT, "我怀疑四号只是因为感觉是他像AI没有公开依据").valid())
                .isFalse();
        assertThat(policy.validate(TuringHuntActionType.RESPOND, "不知道不知道不知道不知道不知道不知道").valid())
                .isFalse();
    }

    @Test
    void modelCompressionStopsAtPunctuationAndRemainsValidUnicode() {
        String raw = "我选择保留地图，因为它能帮助所有人复核方向，也能让后续讨论拥有共同参照。"
                + "这个选择并不完美，但相比删除地图后依赖各自记忆，它造成的误差更容易发现和修正。"
                + "多余的第三句应该被安全压缩掉而不能进入最终公开文本。";

        String normalized = policy.normalizeModelText(TuringHuntActionType.ANSWER, raw);

        assertThat(policy.codePointCount(normalized)).isBetween(30, 90);
        assertThat(normalized).endsWith("。");
        assertThat(policy.validate(TuringHuntActionType.ANSWER, normalized).valid()).isTrue();
    }

    @Test
    void modelTextWithoutAnyBoundaryIsNotCutMidWord() {
        String raw = "我选择保留地图因为这个公开选择可以让所有人持续复核方向避免误解" + "甲".repeat(80);

        String normalized = policy.normalizeModelText(TuringHuntActionType.ANSWER, raw);

        assertThat(normalized).isEqualTo(raw);
        assertThat(policy.validate(TuringHuntActionType.ANSWER, normalized).valid()).isFalse();
    }

    private void assertRule(TuringHuntActionType type, int min, int max, int sentences) {
        TuringTextPolicy.Rule rule = policy.rule(type);
        assertThat(rule.minimum()).isEqualTo(min);
        assertThat(rule.maximum()).isEqualTo(max);
        assertThat(rule.maximumSentences()).isEqualTo(sentences);
    }
}
