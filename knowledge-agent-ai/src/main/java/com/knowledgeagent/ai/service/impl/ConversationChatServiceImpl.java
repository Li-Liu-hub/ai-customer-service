package com.knowledgeagent.ai.service.impl;

import com.knowledgeagent.ai.config.ContextProperties;
import com.knowledgeagent.ai.config.SummaryAgent;
import com.knowledgeagent.ai.pojo.dto.ConversationChatRequest;
import com.knowledgeagent.ai.pojo.dto.ConversationChatResponse;
import com.knowledgeagent.ai.service.ConversationChatService;
import com.knowledgeagent.common.aop.OperationLog;
import com.knowledgeagent.common.aop.OperationLogContext;
import com.knowledgeagent.common.exception.error.ConversationError;
import com.knowledgeagent.common.util.ChatResponseUtil;
import com.knowledgeagent.common.util.FormatUtil;
import com.knowledgeagent.common.util.TokenEstimateUtil;
import com.knowledgeagent.conversation.config.ConversationProperties;
import com.knowledgeagent.conversation.pojo.entity.Conversation;
import com.knowledgeagent.conversation.pojo.entity.Message;
import com.knowledgeagent.conversation.service.ConversationService;
import com.knowledgeagent.mcp.support.ExternalToolProvider;
import com.knowledgeagent.systemlog.pojo.OperationType;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * 多轮对话编排：无会话则创建、持久化消息、按摘要水位线回放历史；
 * token预算占模型窗口达到压缩触发比例（如90%）时折叠旧消息进摘要（水位线只进不退），
 * 压后回落到窗口的15%-25%；同一会话同时只允许一轮对话在途，首轮完成后生成会话标题。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationChatServiceImpl implements ConversationChatService {

  /** 智能客服Agent（带知识检索工具）。 */
  private final ChatClient agentChatClient;

  /** 会话摘要Agent。 */
  private final SummaryAgent summaryAgent;

  /** 会话标题生成Agent（根据首轮用户消息生成会话标题）。 */
  private final ChatClient titleChatClient;

  /** 会话存储能力。 */
  private final ConversationService conversationService;

  /** 上下文预算配置。 */
  private final ContextProperties contextProperties;

  /** 会话配置（标题长度上限）。 */
  private final ConversationProperties conversationProperties;

  /** 外部 MCP 工具提供者（仅智能客服 Agent 挂载，摘要压缩等辅助 Agent 不挂）。 */
  private final ExternalToolProvider externalToolProvider;

  /**
   * 多轮对话主流程：无会话则新建、有会话则加载；先落库用户消息，再按摘要水位线回放活跃历史，
   * Token预算达到压缩触发比例时折叠最旧消息进摘要；调用智能客服Agent（挂载知识检索与外部MCP工具）
   * 生成回复并回填落库，首轮对话完成后生成会话标题。
   *
   * @param request 对话请求（会话ID为空表示新建会话）
   * @return 会话ID与AI回复文本
   * @throws com.knowledgeagent.common.exception.BusinessException 会话不存在、会话繁忙或摘要/回复为空时抛出
   */
  @Override
  @OperationLog(
      type = OperationType.AGENT_CHAT,
      detail = "{'conversationId': #result.conversationId()}")
  public ConversationChatResponse chat(ConversationChatRequest request) {
    // 无会话ID则新建会话（首轮），否则加载既有会话（不存在时由下层抛业务异常）
    Conversation conversation =
        request.getConversationId() == null
            ? conversationService.createConversation()
            : conversationService.getConversation(request.getConversationId());
    // 并发保护：同一会话同时只允许一轮对话在途，完成后（含失败）释放处理权
    if (!conversationService.tryAcquireProcessing(conversation.getId())) {
      throw ConversationError.CONVERSATION_BUSY.exception();
    }
    try {
      // 先落库本次用户消息（含Token估算），再取水位线后的活跃消息构建历史
      Message currentUser =
          conversationService.saveUserMessage(
              conversation.getId(),
              request.getMessage(),
              TokenEstimateUtil.countTokens(request.getMessage()));
      List<Message> active = conversationService.getActiveMessages(conversation.getId());
      // 摘要+活跃历史Token总量超压缩阈值时，折叠最旧消息进摘要，返回仍保留的活跃消息
      List<Message> remaining = maybeCompress(conversation, active);

      // 组装模型输入:各轮次用户/助手消息
      List<org.springframework.ai.chat.messages.Message> modelMessages =
          buildModelMessages(conversation, remaining);
      // 调用智能客服Agent：挂载知识检索与外部MCP工具，由模型自主决定是否调用
      ChatResponse response =
          agentChatClient
              .prompt()
              .messages(modelMessages)
              .tools((Object[]) externalToolProvider.toolCallbacks())
              .call()
              .chatResponse();
      // 将本次模型调用名称与Token消耗记入操作日志上下文
      OperationLogContext.add(response);
      String answer = ChatResponseUtil.extractText(response);
      // 空回复视为生成失败：不落库AI消息，直接抛业务异常
      if (answer == null || answer.isBlank()) {
        throw ConversationError.AI_RESPONSE_EMPTY.exception();
      }
      // 将AI回复回填到该轮消息（含Token估算）；首轮对话再生成会话标题
      conversationService.completeAssistantMessage(
          currentUser.getId(), answer, TokenEstimateUtil.countTokens(answer));
      maybeGenerateTitle(conversation, request.getMessage());
      return new ConversationChatResponse(conversation.getId(), answer);
    } finally {
      // 无论成功或失败都释放处理权，允许该会话开始下一轮对话
      conversationService.completeProcessing(conversation.getId());
    }
  }

  /**
   * 组装发给模型的消息序列：交接摘要原样作为SystemMessage注入
   * （处理说明见智能客服系统提示词的对话背景），随后按序为各消息轮次的用户与AI文本。
   *
   * @param conversation 会话（含最新摘要）
   * @param remaining 未压缩进摘要的活跃消息
   * @return Spring AI消息列表
   */
  private List<org.springframework.ai.chat.messages.Message> buildModelMessages(
      Conversation conversation, List<Message> remaining) {
    List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
    if (conversation.getSummary() != null && !conversation.getSummary().isBlank()) {
      messages.add(new SystemMessage(conversation.getSummary()));
    }
    for (Message m : remaining) {
      messages.add(new UserMessage(m.getUserMessage()));
      if (m.getAiMessage() != null && !m.getAiMessage().isBlank()) {
        messages.add(new AssistantMessage(m.getAiMessage()));
      }
    }
    return messages;
  }

  /**
   * 当摘要与活跃历史的Token总量达到窗口的压缩触发比例（如90%）时执行压缩：
   * 折叠范围由会话模块选定（从最旧消息起折叠，当前轮永不折叠），
   * 本方法负责调用摘要模型生成新摘要，并回写摘要与水位线。
   *
   * @param conversation 当前会话
   * @param active 活跃消息（按创建时间正序，最后一条为本次用户消息）
   * @return 折叠后仍保留的活跃消息
   */
  private List<Message> maybeCompress(Conversation conversation, List<Message> active) {
    int window = contextProperties.contextWindowTokens();
    if (!conversationService.isContextBudgetReached(
        conversation.getId(), window, contextProperties.compactTriggerRatio())) {
      return active;
    }
    List<Message> toFold =
        conversationService.selectMessagesForCompression(
            conversation.getId(), window, contextProperties.compactTargetRatio());
    if (toFold.isEmpty()) {
      // 压无可压（仅剩当前轮），保持原样
      return active;
    }

    String newSummary = summaryAgent.summarize(conversation.getSummary(), toFold);
    conversation.setSummary(newSummary);
    Long summarizedUntilId = toFold.get(toFold.size() - 1).getId();
    conversation.setSummarizedUntilId(summarizedUntilId);
    int newSummaryTokens = TokenEstimateUtil.countTokens(newSummary);
    conversationService.applySummary(
        conversation.getId(), newSummary, summarizedUntilId, newSummaryTokens);

    // 折叠范围与活跃消息取自同一水位线，折叠条数一一对应
    List<Message> remaining = active.subList(toFold.size(), active.size());
    log.info(
        "会话{}完成压缩：折叠{}条活跃消息进摘要，剩余{}条；新摘要约{}tokens，窗口{}tokens",
        conversation.getId(),
        toFold.size(),
        remaining.size(),
        newSummaryTokens,
        window);
    return remaining;
  }

  /**
   * 首轮对话（会话尚无标题）后，根据用户第一段消息生成会话标题；
   * 模型不可用时降级为消息截断，保证标题总有值。
   *
   * @param conversation 当前会话
   * @param firstUserMessage 首轮用户消息
   */
  private void maybeGenerateTitle(Conversation conversation, String firstUserMessage) {
    if (conversation.getTitle() != null && !conversation.getTitle().isBlank()) {
      return;
    }
    int maxLength = conversationProperties.titleMaxLength();
    String title = null;
    try {
      ChatResponse response =
          titleChatClient.prompt().user(firstUserMessage).call().chatResponse();
      OperationLogContext.add(response);
      String titleText = ChatResponseUtil.extractText(response);
      title = FormatUtil.normalizeConversationTitle(titleText, maxLength);
    } catch (Exception e) {
      log.warn("会话标题生成失败，降级使用消息截断：{}", e.getMessage());
    }
    if (title == null || title.isBlank()) {
      title = FormatUtil.normalizeConversationTitle(firstUserMessage, maxLength);
    }
    if (title != null && !title.isBlank()) {
      try {
        conversationService.updateTitle(conversation.getId(), title);
        conversation.setTitle(title);
      } catch (RuntimeException e) {
        log.warn("保存会话标题失败：{}", e.getMessage());
      }
    }
  }
}
