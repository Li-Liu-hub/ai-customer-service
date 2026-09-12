package com.knowledgeagent.ai.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 最基本对话中历史对话的单个轮次。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BasicChatTurn {

  /** 角色，仅支持 user 或 assistant。 */
  private String role;

  /** 该轮次的文本内容。 */
  private String content;
}