package com.knowledgeagent.ai.service.impl;

import com.knowledgeagent.ai.config.ContextProperties;
import com.knowledgeagent.ai.pojo.dto.ConversationChatRequest;
import com.knowledgeagent.ai.pojo.dto.ConversationChatResponse;
import com.knowledgeagent.ai.service.ConversationChatService;
import com.knowledgeagent.common.exception.error.ConversationError;
import com.knowledgeagent.conversation.pojo.entity.Conversation;
import com.knowledgeagent.conversation.pojo.entity.Message;
import com.knowledgeagent.conversation.service.ConversationService;
import com.knowledgeagent.mcp.support.ExternalToolProvider;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

/**
 * 多轮对话编排：无会话则创建、持久化消息、按时间戳边界回放历史；
 * token预算占模型窗口达到压缩触发比例（如90%）时折叠旧消息进摘要，压后回落到窗口的15%-25%。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationChatServiceImpl implements ConversationChatService {

  /** jtokkit编码注册表（cl100k_base），用于近似估算中文文本Token数。 */
  private static final EncodingRegistry ENCODING_REGISTRY = Encodings.newDefaultEncodingRegistry();

  /** token估算编码器（模型tokenizer的近似，中文常见字约1-1.5 token/字）。 */
  private static final Encoding TOKEN_ENCODING =
      ENCODING_REGISTRY.getEncodingForModel(ModelType.GPT_4O_MINI);

  /** 智能客服Agent（带知识检索工具）。 */
  private final ChatClient agentChatClient;

  /** 会话摘要压缩Agent。 */
  private final ChatClient summaryChatClient;

  /** 会话存储能力。 */
  private final ConversationService conversationService;

  /** 上下文预算配置。 */
  private final ContextProperties contextProperties;

  /** 外部 MCP 工具提供者（仅智能客服 Agent 挂载，basicChatAgent 与 summaryChatClient 不挂）。 */
  private final ExternalToolProvider externalToolProvider;

  @Override
  public ConversationChatResponse chat(ConversationChatRequest request) {
    Conversation conversation =
        request.getConversationId() == null
            ? conversationService.createConversation()
            : conversationService.getConversation(request.getConversationId());

    // 先落库本次用户消息，再以其create_time晚于update_time的活跃消息构建历史
    Message currentUser = conversationService.saveUserMessage(conversation.getId(), request.getMessage());
    List<Message> active = conversationService.getMessagesAfter(conversation.getId(), conversation.getUpdateTime());
    List<Message> remaining = maybeCompress(conversation, active);

    List<org.springframework.ai.chat.messages.Message> modelMessages =
        buildModelMessages(conversation, remaining);
    String answer =
        agentChatClient
            .prompt()
            .messages(modelMessages)
            .tools((Object[]) externalToolProvider.toolCallbacks())
            .call()
            .content();
    if (answer == null || answer.isBlank()) {
      throw ConversationError.AI_RESPONSE_EMPTY.exception();
    }
    conversationService.completeAssistantMessage(currentUser.getId(), answer);
    return new ConversationChatResponse(conversation.getId(), answer);
  }

  /**
   * 组装发给模型的消息序列：摘要包装为交接说明的SystemMessage，随后按序为各消息轮次的用户与AI文本。
   *
   * @param conversation 会话（含最新摘要）
   * @param remaining 未压缩进摘要的活跃消息
   * @return Spring AI消息列表
   */
  private List<org.springframework.ai.chat.messages.Message> buildModelMessages(
      Conversation conversation, List<Message> remaining) {
    List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
    if (conversation.getSummary() != null && !conversation.getSummary().isBlank()) {
      messages.add(
          new SystemMessage(
              "此前的对话已压缩为以下交接摘要。请把摘要当作已知背景，结合最近的对话继续服务用户，"
                  + "不要重复询问摘要中已有信息：\n"
                  + conversation.getSummary()));
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
   * 当摘要与活跃历史的token总量达到窗口的压缩触发比例（如90%）时执行压缩：
   * 从最旧消息起折叠进摘要，使压缩后「新摘要+剩余活跃」约落在窗口的15%-25%
   * （活跃侧预留目标的一半配额，其余空间留给新摘要）。当前轮永不折叠；
   * 若活跃侧本来就在配额内而摘要自身过大，则除当前轮外全部折叠以重新生成受控长度的摘要。
   *
   * @param conversation 当前会话
   * @param active 活跃消息（按创建时间正序，最后一条为本次用户消息）
   * @return 折叠后仍保留的活跃消息
   */
  private List<Message> maybeCompress(Conversation conversation, List<Message> active) {
    int window = contextProperties.contextWindowTokens();
    int triggerTokens = (int) (window * contextProperties.compactTriggerRatio());
    int summaryTokens = countTokens(conversation.getSummary());
    int activeTotal = activeTokens(active);
    if (summaryTokens + activeTotal <= triggerTokens) {
      return active;
    }

    int activeQuota = (int) (window * contextProperties.compactTargetRatio() / 2);
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
    if (foldCount <= 0) {
      // 压无可压（仅剩当前轮），保持原样
      return active;
    }

    List<Message> toFold = new ArrayList<>(active.subList(0, foldCount));
    String newSummary = compress(conversation, toFold);
    conversation.setSummary(newSummary);
    conversationService.applySummary(
        conversation.getId(), newSummary, toFold.get(toFold.size() - 1).getCreateTime());

    List<Message> remaining = active.subList(foldCount, active.size());
    int afterTokens = countTokens(newSummary) + activeTokens(remaining);
    log.info(
        "会话{}完成压缩：触发前约{}tokens，压后约{}tokens（占窗口{}的{}%）",
        conversation.getId(),
        summaryTokens + activeTotal,
        afterTokens,
        window,
        Math.round(100.0 * afterTokens / window));
    return remaining;
  }

  /**
   * 调用摘要Agent，把旧摘要与待压缩轮次合并为一份新的交接摘要。
   *
   * @param conversation 当前会话（含旧摘要）
   * @param toFold 待压缩进摘要的消息
   * @return 新会话摘要
   */
  private String compress(Conversation conversation, List<Message> toFold) {
    StringBuilder content = new StringBuilder();
    if (conversation.getSummary() != null && !conversation.getSummary().isBlank()) {
      content.append("已有摘要：\n").append(conversation.getSummary()).append("\n\n");
    }
    content.append("以下为新对话内容，请把关键信息合并进摘要：\n");
    for (Message m : toFold) {
      content.append("用户：").append(m.getUserMessage()).append("\n");
      if (m.getAiMessage() != null && !m.getAiMessage().isBlank()) {
        content.append("助手：").append(m.getAiMessage()).append("\n");
      }
    }
    String summary = summaryChatClient.prompt().user(content.toString()).call().content();
    if (summary == null || summary.isBlank()) {
      throw ConversationError.SUMMARY_GENERATION_EMPTY.exception();
    }
    return summary;
  }

  /**
   * 估算文本Token数（cl100k近似，中文常见字约1-1.5 token/字）。
   *
   * @param text 文本；null或空白返回0
   * @return 估算Token数
   */
  private static int countTokens(String text) {
    if (text == null || text.isBlank()) {
      return 0;
    }
    return TOKEN_ENCODING.countTokens(text);
  }

  /**
   * 估算单轮消息（用户消息+AI回复）的Token数。
   *
   * @param message 消息轮
   * @return 估算Token数
   */
  private static int messageTokens(Message message) {
    return countTokens(message.getUserMessage()) + countTokens(message.getAiMessage());
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
