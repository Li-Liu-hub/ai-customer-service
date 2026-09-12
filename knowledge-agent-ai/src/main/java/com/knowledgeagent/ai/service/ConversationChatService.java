package com.knowledgeagent.ai.service;

import com.knowledgeagent.ai.pojo.dto.ConversationChatRequest;
import com.knowledgeagent.ai.pojo.dto.ConversationChatResponse;

/** 多轮对话编排服务接口。 */
public interface ConversationChatService {

  /**
   * 处理一次多轮对话：无会话则创建、持久化消息、按3000字窗口压缩摘要并生成回答。
   *
   * @param request 会话ID与当前用户消息
   * @return 会话ID与AI回答
   */
  ConversationChatResponse chat(ConversationChatRequest request);
}