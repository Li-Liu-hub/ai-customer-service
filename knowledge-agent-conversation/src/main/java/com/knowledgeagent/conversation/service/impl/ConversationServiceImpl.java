package com.knowledgeagent.conversation.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.knowledgeagent.common.exception.error.ConversationError;
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
  public Message saveUserMessage(Long conversationId, String userText) {
    Message message = new Message();
    message.setId(IdWorker.getId());
    message.setConversationId(conversationId);
    message.setUserMessage(userText);
    message.setCreateTime(OffsetDateTime.now());
    messageMapper.insert(message);
    return message;
  }

  @Override
  public void completeAssistantMessage(Long messageId, String aiText) {
    messageMapper.updateAssistantMessage(messageId, aiText);
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
  public List<Message> getMessagesAfter(Long conversationId, OffsetDateTime afterTime) {
    return messageMapper.selectAfterTime(conversationId, afterTime);
  }

  @Override
  public void applySummary(Long conversationId, String summary, OffsetDateTime boundaryTime) {
    conversationMapper.updateSummary(conversationId, summary, boundaryTime);
  }
}