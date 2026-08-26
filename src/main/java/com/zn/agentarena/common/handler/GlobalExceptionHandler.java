package com.zn.agentarena.common.handler;

import com.zn.agentarena.common.Response;
import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常。
     *
     * @param exception 业务异常
     * @return 错误响应
     */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Response<Void>> handleCustomException(CustomException exception) {
        ResultCodeEnum resultCode = exception.getResultCodeEnum();
        log.warn("业务异常: code={}, message={}", resultCode.getCode(), exception.getMessage());
        return ResponseEntity.status(resultCode.getHttpStatus())
                .body(Response.failWithCustomMessage(resultCode, exception.getMessage()));
    }

    /**
     * 处理请求体字段校验异常。
     *
     * @param exception 字段校验异常
     * @return 错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage() == null ? error.getField() + "不合法" : error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return badRequest(ResultCodeEnum.PARAMS_ERROR, message);
    }

    /**
     * 处理方法参数约束异常。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Response<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return badRequest(ResultCodeEnum.PARAMS_ERROR, message);
    }

    /**
     * 处理缺少参数异常。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Response<Void>> handleMissingParameter(MissingServletRequestParameterException exception) {
        return badRequest(ResultCodeEnum.MISS_PARAMS, "缺少必要参数: " + exception.getParameterName());
    }

    /**
     * 处理JSON解析异常。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Response<Void>> handleMessageNotReadable() {
        return badRequest(ResultCodeEnum.JSON_WRONG, ResultCodeEnum.JSON_WRONG.getMsg());
    }

    /**
     * 处理不支持的请求方法。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Response<Void>> handleMethodNotSupported() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Response.fail(ResultCodeEnum.HTTP_METHOD_ERROR));
    }

    /**
     * 处理资源不存在异常。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Response<Void>> handleNoResourceFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Response.fail(ResultCodeEnum.RESOURCE_NOT_FOUND));
    }

    /**
     * Handles a client disconnect after a streaming response has already started.
     *
     * <p>An SSE write failure is dispatched back through Spring MVC's exception
     * resolvers. At that point the response is already committed, so attempting to
     * serialize the regular JSON error body only causes a second conversion error.</p>
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<Response<Void>> handleIOException(IOException exception,
                                                            HttpServletResponse response) {
        if (response.isCommitted()) {
            log.debug("客户端已断开，忽略已提交响应的I/O异常: {}", exception.getMessage());
            return null;
        }
        return handleUnexpectedException(exception);
    }

    /**
     * 处理未预期异常。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> handleUnexpectedException(Exception exception) {
        log.error("未处理的系统异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Response.fail(ResultCodeEnum.FAIL));
    }

    private ResponseEntity<Response<Void>> badRequest(ResultCodeEnum resultCode, String message) {
        return ResponseEntity.badRequest().body(Response.failWithCustomMessage(resultCode, message));
    }
}
