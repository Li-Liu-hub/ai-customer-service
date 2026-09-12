package com.knowledgeagent.conversation.service;

import com.knowledgeagent.conversation.pojo.entity.Conversation;
import com.knowledgeagent.conversation.pojo.entity.Message;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 会话存储能力，供上层ai模块编排多轮对话使用。
 * 本模块不感知大模型，只负责会话与消息的持久化、历史回调与摘要分界时间的存储。
 */
public interface ConversationService {

  /**
   * 使用雪花算法创建一个新会话。
   *
   * @return 新建的会话实体
   */
  Conversation createConversation();

  /**
   * 在指定会话下记录一条用户消息，返回生成的消息行（用于后续回填AI回复）。
   *
   * @param conversationId 会话ID
   * @param userText 用户消息内容
   * @return 已落库的消息实体
   */
  Message saveUserMessage(Long conversationId, String userText);

  /**
   * 回填某条消息的AI回复。
   *
   * @param messageId 消息ID
   * @param aiText AI回复内容
   */
  void completeAssistantMessage(Long messageId, String aiText);

  /**
   * 按会话ID查询会话，不存在时抛出业务异常。
   *
   * @param conversationId 会话ID
   * @return 会话实体
   */
  Conversation getConversation(Long conversationId);

  /**
   * 查询会话中创建时间晚于指定分界时间的全部消息，按创建时间正序返回。
   *
   * @param conversationId 会话ID
   * @param afterTime 历史分界时间；null表示从最早开始
   * @return 活跃消息列表
   */
  List<Message> getMessagesAfter(Long conversationId, OffsetDateTime afterTime);

  /**
   * 写入新的会话摘要，并把会话更新时间更新为新的历史分界时间。
   *
   * @param conversationId 会话ID
   * @param summary 新的会话摘要
   * @param boundaryTime 新的历史分界时间
   */
  void applySummary(Long conversationId, String summary, OffsetDateTime boundaryTime);
}