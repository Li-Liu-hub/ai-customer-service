package com.knowledgeagent.conversation.service;

import com.knowledgeagent.conversation.pojo.vo.ConversationMessageVO;
import com.knowledgeagent.conversation.pojo.vo.ConversationSummaryVO;
import java.util.List;

/** 会话查询能力：面向接口层提供会话列表与历史消息的读取。 */
public interface ConversationQueryService {

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
