package com.zn.agentarena.modules.game.engine;

import com.zn.agentarena.common.enums.ResultCodeEnum;

/**
 * 游戏行动校验结果。
 *
 * @param valid 是否通过校验
 * @param resultCode 失败状态码
 * @param message 校验消息
 * @author zhangxin
 * @date 2026/07/12
 */
public record ActionValidation(boolean valid, ResultCodeEnum resultCode, String message) {

    /** 创建校验通过的结果。 */
    public static ActionValidation success() {
        return new ActionValidation(true, ResultCodeEnum.SUCCESS, "校验通过");
    }

    /** 创建包含错误码与提示信息的校验失败结果。 */
    public static ActionValidation fail(ResultCodeEnum resultCode, String message) {
        return new ActionValidation(false, resultCode, message);
    }
}
