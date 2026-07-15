package com.zn.agentarena.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 统一业务状态码。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Getter
@AllArgsConstructor
public enum ResultCodeEnum {

    /** 请求成功。 */
    SUCCESS("00000", "成功", HttpStatus.OK),
    /** 缺少必要参数。 */
    MISS_PARAMS("A0410", "缺少必要参数", HttpStatus.BAD_REQUEST),
    /** 请求参数不合法。 */
    PARAMS_ERROR("A0430", "请求参数不合法", HttpStatus.BAD_REQUEST),
    /** JSON 解析失败。 */
    JSON_WRONG("A0427", "JSON解析失败", HttpStatus.BAD_REQUEST),
    /** 请求的资源不存在。 */
    RESOURCE_NOT_FOUND("A0404", "请求资源不存在", HttpStatus.NOT_FOUND),
    /** HTTP 请求方法不正确。 */
    HTTP_METHOD_ERROR("A0405", "请求方式错误", HttpStatus.METHOD_NOT_ALLOWED),
    /** 用户登录状态已失效。 */
    NO_LOGIN("A0230", "用户登录已过期", HttpStatus.UNAUTHORIZED),
    /** 当前用户没有访问权限。 */
    NO_PERMISSION("A0301", "访问未授权", HttpStatus.FORBIDDEN),
    /** 请求频率超过限制。 */
    TOO_MANY_REQUESTS("A0502", "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    /** 系统执行发生异常。 */
    FAIL("B0001", "系统执行异常", HttpStatus.INTERNAL_SERVER_ERROR),

    /** 用户不存在。 */
    USER_NOT_FOUND("G1001", "用户不存在", HttpStatus.NOT_FOUND),
    /** 用户名已经被占用。 */
    USERNAME_ALREADY_EXISTS("G1002", "用户名已被使用", HttpStatus.CONFLICT),
    /** 登录凭据不正确。 */
    LOGIN_FAILED("A0210", "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    /** 模型配置不存在。 */
    MODEL_CONFIG_NOT_FOUND("G2001", "模型配置不存在", HttpStatus.NOT_FOUND),
    /** 模型调用失败。 */
    MODEL_CALL_FAILED("G2002", "模型调用失败", HttpStatus.BAD_GATEWAY),
    /** 模型调用超时。 */
    MODEL_CALL_TIMEOUT("G2003", "模型调用超时", HttpStatus.GATEWAY_TIMEOUT),
    /** 模型返回内容不符合预期。 */
    MODEL_RESPONSE_INVALID("G2004", "模型返回内容不合法", HttpStatus.UNPROCESSABLE_ENTITY),
    /** 游戏不存在。 */
    GAME_NOT_FOUND("G3001", "游戏不存在", HttpStatus.NOT_FOUND),
    /** 当前游戏状态不允许该操作。 */
    GAME_STATUS_ERROR("G3002", "当前游戏状态不允许执行该操作", HttpStatus.CONFLICT),
    /** 游戏已经开始。 */
    GAME_ALREADY_STARTED("G3003", "游戏已经开始", HttpStatus.CONFLICT),
    /** 游戏席位已满。 */
    GAME_PLAYER_FULL("G3004", "游戏席位已满", HttpStatus.CONFLICT),
    /** 游戏玩家不存在。 */
    PLAYER_NOT_FOUND("G3101", "玩家不存在", HttpStatus.NOT_FOUND),
    /** 游戏玩家已经出局。 */
    PLAYER_ALREADY_ELIMINATED("G3102", "玩家已经出局", HttpStatus.CONFLICT),
    /** 当前阶段不允许执行该行动。 */
    ACTION_NOT_ALLOWED("G3201", "当前阶段不允许执行该行动", HttpStatus.CONFLICT),
    /** 行动目标不合法。 */
    ACTION_TARGET_INVALID("G3202", "行动目标不合法", HttpStatus.BAD_REQUEST),
    /** 当前游戏阶段不匹配。 */
    GAME_PHASE_MISMATCH("G3203", "游戏阶段不匹配", HttpStatus.CONFLICT),
    /** 游戏引擎执行异常。 */
    GAME_ENGINE_ERROR("G3901", "游戏引擎执行异常", HttpStatus.INTERNAL_SERVER_ERROR);

    /** 业务状态码。 */
    private final String code;
    /** 面向调用方的状态说明。 */
    private final String msg;
    /** 对应的 HTTP 状态码。 */
    private final HttpStatus httpStatus;
}
