package com.knowledgeagent.common.util;

import org.springframework.ai.chat.model.ChatResponse;

/**
 * 聊天响应工具：从Spring AI聊天响应中提取文本内容，供各Agent的模型调用统一复用。
 */
public final class ChatResponseUtil {

  private ChatResponseUtil() {}

  /**
   * 提取聊天响应中的文本内容。
   *
   * @param response 聊天响应，可能为null
   * @return 响应文本；响应结构不完整时返回null
   */
  public static String extractText(ChatResponse response) {
    return response == null || response.getResult() == null
        ? null
        : response.getResult().getOutput().getText();
  }
}
