package com.knowledgeagent.common.response;

/**
 * REST 接口统一响应，包含业务结果。
 *
 * @param code 业务码；成功时为 0
 * @param message 面向调用方的结果说明
 * @param data 业务数据
 */
public record ApiResponse<T>(int code, String message, T data) {
  /**
   * 构建成功响应。
   *
   * @param data 业务数据
   * @return 成功响应
   */
  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(0, "success", data);
  }

  /**
   * 构建不包含业务数据的错误响应。
   *
   * @param code 错误码或状态码
   * @param message 可展示的错误说明
   * @return 错误响应
   */
  public static ApiResponse<Void> error(int code, String message) {
    return new ApiResponse<>(code, message, null);
  }
}