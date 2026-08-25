package com.zn.agentarena.modules.model.domain;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;

/**
 * 面向战报的安全模型失败分类，不暴露供应商原始响应。
 *
 * @param code code 字段
 * @param summary summary 字段
 */
public record ModelFailureInfo(String code, String summary) {

    public static ModelFailureInfo from(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CustomException customException) {
                ResultCodeEnum resultCode = customException.getResultCodeEnum();
                if (resultCode == ResultCodeEnum.MODEL_RESPONSE_INVALID) {
                    return new ModelFailureInfo("STRUCTURED_OUTPUT",
                            "模型响应已自动清洗并尝试修复，但仍无法生成有效决策，本回合改用本地策略。");
                }
                if (resultCode == ResultCodeEnum.MODEL_CALL_TIMEOUT) {
                    return new ModelFailureInfo("MODEL_TIMEOUT",
                            "模型服务响应超时，本回合已使用本地策略继续对局。");
                }
                if (resultCode == ResultCodeEnum.MODEL_CALL_FAILED) {
                    return new ModelFailureInfo("MODEL_UNAVAILABLE",
                            "模型服务暂时不可用，本回合已使用本地策略继续对局。");
                }
                if (resultCode == ResultCodeEnum.TOO_MANY_REQUESTS) {
                    return rateLimited();
                }
            }
            current = current.getCause();
        }
        return new ModelFailureInfo("MODEL_ERROR",
                "模型决策出现未预期错误，本回合已使用本地策略继续对局。");
    }

    /**
     * 创建表示模型调用触发限流的安全失败信息。
     *
     * @return 限流失败信息
     */
    public static ModelFailureInfo rateLimited() {
        return new ModelFailureInfo("MODEL_RATE_LIMIT",
                "模型调用已达到当前频率保护上限，本回合使用本地策略，自动对局仍会继续。");
    }
}
