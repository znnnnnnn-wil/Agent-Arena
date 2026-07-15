package com.zn.agentarena.common.exceptions;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import lombok.Getter;

/**
 * 业务异常。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Getter
public class CustomException extends RuntimeException {

    private final ResultCodeEnum resultCodeEnum;

    private CustomException(ResultCodeEnum resultCodeEnum, String message) {
        super(message == null ? resultCodeEnum.getMsg() : message);
        this.resultCodeEnum = resultCodeEnum;
    }

    /**
     * 根据业务状态创建异常。
     *
     * @param resultCodeEnum 业务状态
     * @return 业务异常
     */
    public static CustomException create(ResultCodeEnum resultCodeEnum) {
        return new CustomException(resultCodeEnum, null);
    }

    /**
     * 根据业务状态和自定义消息创建异常。
     *
     * @param resultCodeEnum 业务状态
     * @param message 自定义消息
     * @return 业务异常
     */
    public static CustomException create(ResultCodeEnum resultCodeEnum, String message) {
        return new CustomException(resultCodeEnum, message);
    }
}
