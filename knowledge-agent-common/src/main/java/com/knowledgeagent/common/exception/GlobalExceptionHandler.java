package com.knowledgeagent.common.exception;

import com.knowledgeagent.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 将 REST 请求中的异常转换为统一错误响应，并避免敏感信息进入日志。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  /**
   * 返回业务异常自带的错误码、说明和 HTTP 状态。
   *
   * @param e 业务异常
   * @return 统一错误响应
   */
  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ApiResponse<Void>> business(BusinessException e) {
    return response(e.getCode(), e.getMessage(), HttpStatus.valueOf(e.getHttpStatus()));
  }

  /**
   * 返回请求体字段校验错误，并优先展示第一个字段错误说明。
   *
   * @param e 请求体校验异常
   * @return HTTP 400 错误响应
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().isEmpty()
            ? "invalid request"
            : e.getBindingResult().getFieldErrors().getFirst().getDefaultMessage();
    return response(400, message, HttpStatus.BAD_REQUEST);
  }

  /**
   * 返回路径或查询参数的约束校验错误。
   *
   * @param e 参数约束异常
   * @return HTTP 400 错误响应
   */
  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ApiResponse<Void>> validation(ConstraintViolationException e) {
    return response(400, "invalid request", HttpStatus.BAD_REQUEST);
  }

  /**
   * 返回无法转换查询参数类型的错误。
   *
   * @param e 参数类型不匹配异常
   * @return HTTP 400 错误响应
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ApiResponse<Void>> mismatch(MethodArgumentTypeMismatchException e) {
    return response(400, "invalid request parameter", HttpStatus.BAD_REQUEST);
  }

  /**
   * 隐藏未知异常的内部细节，仅向调用方返回通用错误信息并写入服务端日志。
   *
   * @param e 未被其他处理器接管的异常
   * @return HTTP 500 错误响应
   */
  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiResponse<Void>> unknown(Exception e) {
    log.error(
        "Unhandled request failure type={} message={}",
        e.getClass().getSimpleName(),
        e.getMessage());
    return response(500, "internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
  }

  /**
   * 将错误信息包装成项目统一响应。
   *
   * @param code 错误码或状态码
   * @param message 面向调用方的错误说明
   * @param status HTTP 状态
   * @return 统一错误响应
   */
  private ResponseEntity<ApiResponse<Void>> response(int code, String message, HttpStatus status) {
    return ResponseEntity.status(status).body(ApiResponse.error(code, message));
  }
}
