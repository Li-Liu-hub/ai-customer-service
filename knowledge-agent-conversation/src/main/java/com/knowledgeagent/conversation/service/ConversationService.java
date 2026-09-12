package com.knowledgeagent.conversation.service;

import com.knowledgeagent.conversation.pojo.entity.Conversation;
import com.knowledgeagent.conversation.pojo.entity.Message;
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
   * 在指定会话下记录一条用户消息（含Token估算），返回生成的消息行（用于后续回填AI回复）。
   *
   * @param conversationId 会话ID
   * @param userText 用户消息内容
   * @param userTokens 用户消息的Token数（由上层估算）
   * @return 已落库的消息实体
   */
  Message saveUserMessage(Long conversationId, String userText, int userTokens);

  /**
   * 回填某条消息的AI回复与回复Token数。
   *
   * @param messageId 消息ID
   * @param aiText AI回复内容
   * @param aiTokens AI回复的Token数（由上层估算）
   */
  void completeAssistantMessage(Long messageId, String aiText, int aiTokens);

  /**
   * 按会话ID查询会话，不存在时抛出业务异常。
   *
   * @param conversationId 会话ID
   * @return 会话实体
   */
  Conversation getConversation(Long conversationId);

  /**
   * 查询会话中消息ID大于摘要水位线后的全部消息，按ID正序返回。
   *
   * @param conversationId 会话ID
   * @param afterId 摘要水位线；null表示从最早开始
   * @return 活跃消息列表
   */
  List<Message> getMessagesAfter(Long conversationId, Long afterId);

  /**
   * 查询最近更新的会话列表。
   *
   * @param limit 最大返回数量（内部夹紧到1到200）
   * @return 按更新时间倒序的会话列表
   */
  List<Conversation> listRecentConversations(int limit);

  /**
   * 写入新的会话摘要与摘要水位线（水位线只进不退）。
   *
   * @param conversationId 会话ID
   * @param summary 新的会话摘要
   * @param summarizedUntilId 摘要水位线：ID不超过该值的消息已折叠进摘要
   * @param summaryTokens 摘要的Token数量
   */
  void applySummary(
      Long conversationId, String summary, Long summarizedUntilId, int summaryTokens);

  /**
   * 更新会话标题。
   *
   * @param conversationId 会话ID
   * @param title 会话标题
   */
  void updateTitle(Long conversationId, String title);

  /**
   * 尝试为会话抢占一轮处理权（同一会话并发保护）。
   *
   * @param conversationId 会话ID
   * @return 抢占成功返回true；已有进行中的轮次时返回false
   */
  boolean tryAcquireProcessing(Long conversationId);

  /**
   * 释放会话处理权，标记本轮处理已完成（成功或失败均需调用）。
   *
   * @param conversationId 会话ID
   */
  void completeProcessing(Long conversationId);
}