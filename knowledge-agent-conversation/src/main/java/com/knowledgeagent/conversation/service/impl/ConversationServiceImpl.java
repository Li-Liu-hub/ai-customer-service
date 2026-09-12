package com.knowledgeagent.conversation.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.knowledgeagent.common.exception.error.ConversationError;
import com.knowledgeagent.conversation.config.ConversationProperties;
import com.knowledgeagent.conversation.mapper.ConversationMapper;
import com.knowledgeagent.conversation.mapper.MessageMapper;
import com.knowledgeagent.conversation.pojo.entity.Conversation;
import com.knowledgeagent.conversation.pojo.entity.Message;
import com.knowledgeagent.conversation.service.ConversationService;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 会话存储能力实现。 */
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

  /** 会话表数据访问。 */
  private final ConversationMapper conversationMapper;

  /** 消息表数据访问。 */
  private final MessageMapper messageMapper;

  /** 会话配置（标题长度、处理锁僵尸超时）。 */
  private final ConversationProperties conversationProperties;

  @Override
  public Conversation createConversation() {
    Conversation conversation = new Conversation();
    conversation.setId(IdWorker.getId());
    conversation.setCreateTime(OffsetDateTime.now());
    conversation.setUpdateTime(OffsetDateTime.now());
    conversationMapper.insert(conversation);
    return conversation;
  }

  @Override
  public Message saveUserMessage(Long conversationId, String userText, int userTokens) {
    Message message = new Message();
    message.setId(IdWorker.getId());
    message.setConversationId(conversationId);
    message.setUserMessage(userText);
    message.setCreateTime(OffsetDateTime.now());
    message.setUserTokens(userTokens);
    messageMapper.insert(message);
    return message;
  }

  @Override
  public void completeAssistantMessage(Long messageId, String aiText, int aiTokens) {
    messageMapper.updateAssistantMessage(messageId, aiText, aiTokens);
  }

  @Override
  public Conversation getConversation(Long conversationId) {
    Conversation conversation = conversationMapper.selectById(conversationId);
    if (conversation == null) {
      throw ConversationError.CONVERSATION_NOT_FOUND.exception();
    }
    return conversation;
  }

  @Override
  public List<Message> getMessagesAfter(Long conversationId, Long afterId) {
    return messageMapper.selectAfterId(conversationId, afterId);
  }

  @Override
  public List<Conversation> listRecentConversations(int limit) {
    int clampedLimit = Math.max(1, Math.min(limit, 200));
    return conversationMapper.selectRecent(clampedLimit);
  }

  @Override
  public void applySummary(
      Long conversationId, String summary, Long summarizedUntilId, int summaryTokens) {
    conversationMapper.updateSummaryAndWatermark(
        conversationId, summary, summarizedUntilId, summaryTokens);
  }

  @Override
  public void updateTitle(Long conversationId, String title) {
    conversationMapper.updateTitle(conversationId, title);
  }

  @Override
  public boolean tryAcquireProcessing(Long conversationId) {
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime staleBefore = now.minus(conversationProperties.processingStaleTimeout());
    return conversationMapper.tryAcquireProcessing(conversationId, now, staleBefore) == 1;
  }

  @Override
  public void completeProcessing(Long conversationId) {
    conversationMapper.completeProcessing(conversationId, OffsetDateTime.now());
  }
}