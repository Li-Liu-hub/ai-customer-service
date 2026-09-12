package com.knowledgeagent.ai.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 接收最基本对话的请求参数。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BasicChatRequest {

  /** 用户当前输入的消息。 */
  @NotBlank(message = "请输入消息")
  private String message;

  /** 此前的对话历史，按时间先后排列，可为空表示单轮对话。 */
  private List<BasicChatTurn> history;
}