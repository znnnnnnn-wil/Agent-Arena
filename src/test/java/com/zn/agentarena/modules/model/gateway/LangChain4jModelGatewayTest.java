package com.zn.agentarena.modules.model.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.config.AgentArenaProperties;
import com.zn.agentarena.modules.model.domain.ModelProvider;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LangChain4j模型调用网关单元测试。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@DisplayName("LangChain4j模型调用网关单元测试")
class LangChain4jModelGatewayTest {

    private ModelFactory modelFactory;
    private ChatModel chatModel;
    private LangChain4jModelGateway modelGateway;
    private ModelRuntimeConfig config;

    @BeforeEach
    void setUp() {
        modelFactory = mock(ModelFactory.class);
        chatModel = mock(ChatModel.class);
        modelGateway = new LangChain4jModelGateway(modelFactory, new ObjectMapper(),
                new ModelCallConcurrencyLimiter(new AgentArenaProperties(null, null, null)));
        config = new ModelRuntimeConfig(ModelProvider.OPENAI, "test-model", null,
                "test-key", 0.2, 512, Duration.ofSeconds(10), 1);
        when(modelFactory.create(config)).thenReturn(chatModel);
    }

    @Test
    @DisplayName("generateStructured: 应将模型JSON转换为指定类型")
    void testGenerateStructuredSuccess() {
        when(chatModel.chat("请选择行动")).thenReturn("""
                ```json
                {"action":"VOTE","targetPlayerId":5,"reasonSummary":"5号发言矛盾"}
                ```
                """);

        TestDecision decision = modelGateway.generateStructured(config, "请选择行动", TestDecision.class);

        assertEquals("VOTE", decision.action());
        assertEquals(5L, decision.targetPlayerId());
        assertEquals("5号发言矛盾", decision.reasonSummary());
    }

    @Test
    @DisplayName("generateStructured: 非法JSON应抛出模型响应异常")
    void testGenerateStructuredInvalidJsonShouldThrowException() {
        when(chatModel.chat("请选择行动")).thenReturn("我选择投票5号");

        CustomException exception = assertThrows(CustomException.class,
                () -> modelGateway.generateStructured(config, "请选择行动", TestDecision.class));

        assertEquals(ResultCodeEnum.MODEL_RESPONSE_INVALID, exception.getResultCodeEnum());
        verify(chatModel, times(2)).chat(anyString());
    }

    @Test
    @DisplayName("generateStructured: 空响应应直接失败且不得请求格式修复")
    void emptyResponseShouldFailWithoutRepair() {
        when(chatModel.chat("请选择行动")).thenReturn("  ");

        CustomException exception = assertThrows(CustomException.class,
                () -> modelGateway.generateStructured(config, "请选择行动", TestDecision.class));

        assertEquals(ResultCodeEnum.MODEL_RESPONSE_INVALID, exception.getResultCodeEnum());
        verify(chatModel).chat("请选择行动");
        assertFalse(modelGateway.lastParserRepair());
    }

    @Test
    @DisplayName("generateStructured: 应从说明文字中提取第一个完整 JSON")
    void testGenerateStructuredShouldExtractBalancedJson() {
        when(chatModel.chat("请选择行动")).thenReturn("""
                我的决定如下：
                {"action":"VOTE","targetPlayerId":5,"reasonSummary":"观察后决定"}
                以上是本回合决策。
                """);

        TestDecision decision = modelGateway.generateStructured(config, "请选择行动", TestDecision.class);

        assertEquals("VOTE", decision.action());
        assertEquals(5L, decision.targetPlayerId());
        verify(chatModel).chat("请选择行动");
    }

    @Test
    @DisplayName("generateStructured: 应兼容全角标点、尾逗号和 snake_case 字段")
    void testGenerateStructuredShouldNormalizeCommonJsonProblems() {
        when(chatModel.chat("请选择行动")).thenReturn("""
                {“action”：“VOTE”，“target_player_id”：5，“reason_summary”：“观察后决定”，}
                """);

        TestDecision decision = modelGateway.generateStructured(config, "请选择行动", TestDecision.class);

        assertEquals("VOTE", decision.action());
        assertEquals(5L, decision.targetPlayerId());
        assertEquals("观察后决定", decision.reasonSummary());
    }

    @Test
    @DisplayName("generateStructured: 清理结构尾逗号时不得改写公开发言正文")
    void tailCommaCleanupDoesNotMutateSpeechText() {
        when(chatModel.chat("请选择行动")).thenReturn("""
                {“publicSpeech”：“请保留正文,}与,]这两个片段”，“suspects”：[3，]，}
                """);

        TextDecision decision = modelGateway.generateStructured(config, "请选择行动", TextDecision.class);

        assertEquals("请保留正文,}与,]这两个片段", decision.publicSpeech());
        assertEquals(List.of(3), decision.suspects());
    }

    @Test
    @DisplayName("generateStructured: 应直接解开大小写不同的 output 包装")
    void knownEnvelopeIsUnwrappedWithoutRepair() {
        when(chatModel.chat("请选择行动")).thenReturn("""
                {"OuTPuT":{"action":"VOTE","targetPlayerId":"5","reasonSummary":"公开票型集中"}}
                """);

        TestDecision decision = modelGateway.generateStructured(config, "请选择行动", TestDecision.class);

        assertEquals(5L, decision.targetPlayerId());
        verify(chatModel).chat("请选择行动");
    }

    @Test
    @DisplayName("generateStructured: 应兼容单引号、无引号字段和带单位的数字")
    void testGenerateStructuredShouldParseRelaxedJsonAndNumericText() {
        when(chatModel.chat("请选择行动")).thenReturn("""
                {action:'VOTE',target_player_id:'5号',reason_summary:'观察后决定',confidence:'80%'}
                """);

        TestDecision decision = modelGateway.generateStructured(config, "请选择行动", TestDecision.class);

        assertEquals("VOTE", decision.action());
        assertEquals(5L, decision.targetPlayerId());
        assertEquals("观察后决定", decision.reasonSummary());
        assertEquals(80, decision.confidence());
        verify(chatModel).chat("请选择行动");
    }

    @Test
    @DisplayName("generateStructured: 首次无法解析时应请求模型修复一次")
    void testGenerateStructuredShouldRepairInvalidResponseOnce() {
        when(chatModel.chat(anyString()))
                .thenReturn("行动是投票5号")
                .thenReturn("""
                        {"decision":{"action":"VOTE","targetPlayerId":5,"reasonSummary":"观察后决定"}}
                        """);

        TestDecision decision = modelGateway.generateStructured(config, "请选择行动", TestDecision.class);

        assertEquals("VOTE", decision.action());
        assertEquals(5L, decision.targetPlayerId());
        verify(chatModel, times(2)).chat(anyString());
    }

    @Test
    @DisplayName("generateStructured: 数组字段输出为单个座位字符串时应规范化为数组")
    void testGenerateStructuredShouldWrapScalarCollectionAndNormalizeSeatNumber() {
        when(chatModel.chat("请选择行动")).thenReturn("""
                {"suspects":"3号","supports":6,"reasonTags":"VOTE_SHIFT"}
                """);

        ListDecision decision = modelGateway.generateStructured(config, "请选择行动", ListDecision.class);

        assertEquals(List.of(3), decision.suspects());
        assertEquals(List.of(6), decision.supports());
        assertEquals(List.of("VOTE_SHIFT"), decision.reasonTags());
        verify(chatModel).chat("请选择行动");
    }

    @Test
    @DisplayName("generate: 应将底层超时单独分类")
    void testGenerateShouldClassifyTimeout() {
        when(chatModel.chat("请选择行动"))
                .thenThrow(new IllegalStateException(new HttpTimeoutException("timeout")));

        CustomException exception = assertThrows(CustomException.class,
                () -> modelGateway.generate(config, "请选择行动"));

        assertEquals(ResultCodeEnum.MODEL_CALL_TIMEOUT, exception.getResultCodeEnum());
    }

    private record TestDecision(String action, Long targetPlayerId, String reasonSummary, Integer confidence) {
    }

    private record ListDecision(List<Integer> suspects, List<Integer> supports, List<String> reasonTags) {
    }

    private record TextDecision(String publicSpeech, List<Integer> suspects) {
    }
}
