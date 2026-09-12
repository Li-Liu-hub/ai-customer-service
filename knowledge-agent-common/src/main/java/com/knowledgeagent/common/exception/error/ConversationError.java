package com.knowledgeagent.common.exception.error;

import com.knowledgeagent.common.exception.BusinessException;

/** 集中维护会话和Agent对话相关的业务错误。 */
public enum ConversationError {
  USER_MESSAGE_REQUIRED(3001, "请输入会话消息", 400),
  CHAT_MODEL_NOT_CONFIGURED(3002, "聊天模型尚未配置", 503),
  AI_RESPONSE_FAILED(3003, "Agent生成回答失败", 502),
  AI_RESPONSE_EMPTY(3004, "Agent没有返回有效回答", 502),
  TITLE_GENERATION_FAILED(3005, "生成会话标题失败", 502),
  TITLE_GENERATION_EMPTY(3006, "大模型没有返回有效标题", 502),
  CONVERSATION_SAVE_FAILED(3007, "保存会话和用户消息失败", 500),
  AI_MESSAGE_UPDATE_FAILED(3008, "保存Agent回答失败", 500),
  TITLE_UPDATE_FAILED(3009, "保存会话标题失败", 500),
  CONVERSATION_NOT_FOUND(3010, "会话不存在", 404),
  SUMMARY_GENERATION_FAILED(3011, "压缩会话历史失败", 502),
  SUMMARY_GENERATION_EMPTY(3012, "大模型没有返回有效摘要", 502),
  SUMMARY_UPDATE_FAILED(3013, "保存会话摘要失败", 500),
  CONVERSATION_BUSY(3014, "该会话有一轮对话正在处理中，请稍后再试", 409);

  /** 业务错误码。 */
  private final int code;

  /** 返回给调用方的错误信息。 */
  private final String message;

  /** HTTP状态码。 */
  private final int httpStatus;

  ConversationError(int code, String message, int httpStatus) {
    this.code = code;
    this.message = message;
    this.httpStatus = httpStatus;
  }

  /**
   * 根据当前会话错误创建业务异常。
   *
   * @return 包含错误码、错误信息和HTTP状态的业务异常
   */
  public BusinessException exception() {
    return new BusinessException(code, message, httpStatus);
  }

  /**
   * 根据当前会话错误和原始异常创建业务异常。
   *
   * @param cause 触发当前会话错误的原始异常
   * @return 保留原始错误原因的业务异常
   */
  public BusinessException exception(Throwable cause) {
    return new BusinessException(code, message, httpStatus, cause);
  }
}
