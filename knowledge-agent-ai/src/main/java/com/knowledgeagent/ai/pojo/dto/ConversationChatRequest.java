package com.knowledgeagent.ai.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 接收多轮对话请求参数。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationChatRequest {

  /** 会话ID；首次对话传null，由系统创建新会话。 */
  private Long conversationId;

  /** 用户当前输入的消息。 */
  @NotBlank(message = "请输入消息")
  @Size(max = 1000, message = "输入文字过多")
  private String message;
}