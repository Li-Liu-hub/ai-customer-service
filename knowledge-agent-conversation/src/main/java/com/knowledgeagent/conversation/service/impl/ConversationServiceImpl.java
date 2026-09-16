package com.knowledgeagent.conversation.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.knowledgeagent.common.exception.error.ConversationError;
import com.knowledgeagent.common.util.TokenEstimateUtil;
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
  public List<Message> getActiveMessages(Long conversationId) {
    Conversation conversation = getConversation(conversationId);
    return getMessagesAfter(conversationId, conversation.getSummarizedUntilId());
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
  public boolean isContextBudgetReached(
      Long conversationId, int windowTokens, double triggerRatio) {
    Conversation conversation = getConversation(conversationId);
    List<Message> active = getMessagesAfter(conversationId, conversation.getSummarizedUntilId());
    int total = TokenEstimateUtil.countTokens(conversation.getSummary()) + activeTokens(active);
    return total > (int) (windowTokens * triggerRatio);
  }

  @Override
  public List<Message> selectMessagesForCompression(
      Long conversationId, int windowTokens, double targetRatio) {
    Conversation conversation = getConversation(conversationId);
    List<Message> active = getMessagesAfter(conversationId, conversation.getSummarizedUntilId());
    int activeQuota = (int) (windowTokens * targetRatio / 2);
    int activeTotal = activeTokens(active);
    int foldCount;
    if (activeTotal > activeQuota) {
      foldCount = 0;
      int kept = activeTotal;
      while (foldCount < active.size() - 1 && kept > activeQuota) {
        kept -= messageTokens(active.get(foldCount));
        foldCount++;
      }
    } else {
      // 活跃侧已在配额内，超限来自摘要自身：除当前轮外全部折叠，靠摘要提示词控制新摘要长度
      foldCount = active.size() - 1;
    }
    return foldCount <= 0 ? List.of() : List.copyOf(active.subList(0, foldCount));
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

  /**
   * 估算单轮消息（用户消息+AI回复）的Token数：优先使用落库的Token值，缺失时回退实时估算。
   *
   * @param message 消息轮
   * @return 估算Token数
   */
  private static int messageTokens(Message message) {
    int userTokens = message.getUserTokens() == null ? 0 : message.getUserTokens();
    if (userTokens <= 0) {
      userTokens = TokenEstimateUtil.countTokens(message.getUserMessage());
    }
    int aiTokens = message.getAiTokens() == null ? 0 : message.getAiTokens();
    if (aiTokens <= 0 && message.getAiMessage() != null) {
      aiTokens = TokenEstimateUtil.countTokens(message.getAiMessage());
    }
    return userTokens + aiTokens;
  }

  /**
   * 估算消息列表的总Token数。
   *
   * @param messages 消息列表
   * @return 估算Token数
   */
  private static int activeTokens(List<Message> messages) {
    int total = 0;
    for (Message message : messages) {
      total += messageTokens(message);
    }
    return total;
  }
}