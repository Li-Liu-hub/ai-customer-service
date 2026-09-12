package com.knowledgeagent.ai.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 多轮对话响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationChatResponse {

  /** 会话ID，首次对话由系统生成并返回，客户端需持久化用于后续轮次。 */
  private Long conversationId;

  /** AI生成的回答。 */
  private String answer;
}