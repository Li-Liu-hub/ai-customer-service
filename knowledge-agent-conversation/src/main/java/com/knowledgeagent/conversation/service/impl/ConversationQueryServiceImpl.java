package com.knowledgeagent.conversation.service.impl;

import com.knowledgeagent.conversation.pojo.vo.ConversationMessageVO;
import com.knowledgeagent.conversation.pojo.vo.ConversationSummaryVO;
import com.knowledgeagent.conversation.service.ConversationQueryService;
import com.knowledgeagent.conversation.service.ConversationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 会话查询实现：读取会话与消息并组装为接口视图。 */
@Service
@RequiredArgsConstructor
public class ConversationQueryServiceImpl implements ConversationQueryService {

  /** 会话存储能力。 */
  private final ConversationService conversationService;

  @Override
  public List<ConversationSummaryVO> listConversations(int limit) {
    return conversationService.listRecentConversations(limit).stream()
        .map(
            conversation ->
                new ConversationSummaryVO(
                    conversation.getId(),
                    conversation.getTitle(),
                    conversation.getCreateTime(),
                    conversation.getUpdateTime()))
        .toList();
  }

  @Override
  public List<ConversationMessageVO> listMessages(Long conversationId) {
    conversationService.getConversation(conversationId);
    return conversationService.getMessagesAfter(conversationId, null).stream()
        .map(
            message ->
                new ConversationMessageVO(
                    message.getId(),
                    message.getUserMessage(),
                    message.getAiMessage(),
                    message.getCreateTime()))
        .toList();
  }
}
