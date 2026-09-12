package com.knowledgeagent.ai.service;

import com.knowledgeagent.ai.pojo.dto.ConversationChatRequest;
import com.knowledgeagent.ai.pojo.dto.ConversationChatResponse;
import com.knowledgeagent.ai.pojo.vo.ConversationMessageVO;
import com.knowledgeagent.ai.pojo.vo.ConversationSummaryVO;
import java.util.List;

/** 多轮对话编排服务接口。 */
public interface ConversationChatService {

  /**
   * 处理一次多轮对话：无会话则创建、持久化消息、按3000字窗口压缩摘要并生成回答。
   *
   * @param request 会话ID与当前用户消息
   * @return 会话ID与AI回答
   */
  ConversationChatResponse chat(ConversationChatRequest request);

  /**
   * 查询最近更新的会话列表。
   *
   * @param limit 最大返回数量
   * @return 会话摘要列表（按更新时间倒序）
   */
  List<ConversationSummaryVO> listConversations(int limit);

  /**
   * 查询指定会话的历史消息（按时间正序）。
   *
   * @param conversationId 会话ID
   * @return 历史消息列表
   */
  List<ConversationMessageVO> listMessages(Long conversationId);
}