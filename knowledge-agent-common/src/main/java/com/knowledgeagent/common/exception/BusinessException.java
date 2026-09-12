package com.knowledgeagent.common.exception;

/** 可安全返回给调用方的业务异常。 */
public class BusinessException extends RuntimeException {
  /** 保存业务错误码。 */
  private final int code;
  /** 保存错误对应的 HTTP 状态码。 */
  private final int httpStatus;

  /**
   * 创建默认返回 HTTP 400 的业务异常。
   *
   * @param code 错误码或状态码
   * @param message 面向调用方的错误说明
   */
  public BusinessException(int code, String message) {
    this(code, message, 400);
  }

  /**
   * 创建带指定 HTTP 状态的业务异常。
   *
   * @param code 错误码或状态码
   * @param message 面向调用方的错误说明
   * @param httpStatus 要返回的 HTTP 状态码
   */
  public BusinessException(int code, String message, int httpStatus) {
    this(code, message, httpStatus, null);
  }

  /**
   * 创建带指定HTTP状态和原始错误原因的业务异常。
   *
   * @param code 业务错误码
   * @param message 面向调用方的错误说明
   * @param httpStatus 要返回的HTTP状态码
   * @param cause 触发当前业务错误的原始异常
   */
  public BusinessException(int code, String message, int httpStatus, Throwable cause) {
    super(message, cause);
    this.code = code;
    this.httpStatus = httpStatus;
  }

  /**
   * 返回业务错误码。
   *
   * @return 业务错误码
   */
  public int getCode() {
    return code;
  }

  /**
   * 返回 HTTP 状态码。
   *
   * @return HTTP 状态码
   */
  public int getHttpStatus() {
    return httpStatus;
  }
}
