package com.zn.agentarena.modules.model.domain;

/**
 * 模型供应商。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
public enum ModelProvider {
    /** OpenAI 官方服务。 */
    OPENAI,
    /** DeepSeek 服务。 */
    DEEPSEEK,
    /** 通义千问服务。 */
    QWEN,
    /** 豆包服务。 */
    DOUBAO,
    /** 百度千帆服务。 */
    QIANFAN,
    /** 智谱 AI 服务。 */
    ZHIPU,
    /** Moonshot 服务。 */
    MOONSHOT,
    /** 零一万物服务。 */
    YI,
    /** MiniMax 服务。 */
    MINIMAX,
    /** 腾讯混元服务。 */
    HUNYUAN,
    /** 百川智能服务。 */
    BAICHUAN,
    /** SiliconFlow 服务。 */
    SILICONFLOW,
    /** 讯飞星火服务。 */
    SPARK,
    /** 兼容 OpenAI API 的服务。 */
    OPENAI_COMPATIBLE,
    /** 本地 Ollama 服务。 */
    OLLAMA
}
