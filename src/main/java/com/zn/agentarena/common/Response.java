package com.zn.agentarena.common;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应体。
 *
 * @param <T> 响应数据类型
 * @author zhangxin
 * @date 2026/07/12
 */
@Data
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Response<T> {

    /**
     * 业务状态码
     */
    private String code;

    /**
     * 响应消息
     */
    private String msg;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 构建带数据的成功响应。
     *
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> Response<T> success(T data) {
        return build(ResultCodeEnum.SUCCESS, data, null);
    }

    /**
     * 构建不带数据的成功响应。
     *
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> Response<T> success() {
        return build(ResultCodeEnum.SUCCESS, null, null);
    }

    /**
     * 构建失败响应。
     *
     * @param resultCode 响应状态
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> Response<T> fail(ResultCodeEnum resultCode) {
        return build(resultCode, null, null);
    }

    /**
     * 构建带自定义消息的失败响应。
     *
     * @param resultCode 响应状态
     * @param message 自定义消息
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> Response<T> failWithCustomMessage(ResultCodeEnum resultCode, String message) {
        return build(resultCode, null, message);
    }

    private static <T> Response<T> build(ResultCodeEnum resultCode, T data, String message) {
        String responseMessage = message == null ? resultCode.getMsg() : message;
        return new Response<>(resultCode.getCode(), responseMessage, data);
    }
}
