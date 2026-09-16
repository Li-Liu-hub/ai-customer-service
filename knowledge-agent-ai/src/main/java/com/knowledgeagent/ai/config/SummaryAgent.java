package com.knowledgeagent.ai.config;

import com.knowledgeagent.common.aop.OperationLogContext;
import com.knowledgeagent.common.exception.error.ConversationError;
import com.knowledgeagent.common.util.ChatResponseUtil;
import com.knowledgeagent.conversation.pojo.entity.Message;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * 会话摘要Agent：把旧摘要与待压缩轮次合并为一份新的交接摘要，
 * 供对话链路在上下文达到压缩水位线时调用（系统提示词见 AgentPrompts.SUMMARY_SYSTEM）。
 */
@RequiredArgsConstructor
public class SummaryAgent {

  /** 摘要压缩Agent（绑定摘要压缩系统提示词）。 */
  private final ChatClient summaryChatClient;

  /**
   * 把旧摘要与待压缩轮次合并为一份新的交接摘要。
   *
   * @param existingSummary 已有的旧摘要；可为空
   * @param toFold 待压缩进摘要的消息轮次
   * @return 新的会话摘要
   * @throws com.knowledgeagent.common.exception.BusinessException 模型未返回有效摘要时抛出
   */
  public String summarize(String existingSummary, List<Message> toFold) {
    ChatResponse response =
        summaryChatClient
            .prompt()
            .user(compactMaterial(existingSummary, toFold))
            .call()
            .chatResponse();
    OperationLogContext.add(response);
    String summary = ChatResponseUtil.extractText(response);
    if (summary == null || summary.isBlank()) {
      throw ConversationError.SUMMARY_GENERATION_EMPTY.exception();
    }
    return summary;
  }

  /**
   * 组装摘要压缩所需的用户侧素材：已有摘要（若有）在前，后接新对话轮次（用户：/助手：逐条）。
   *
   * @param existingSummary 已有的旧摘要；可为空
   * @param toFold 待压缩进摘要的消息轮次
   * @return 供摘要合并的素材文本
   */
  private static String compactMaterial(String existingSummary, List<Message> toFold) {
    StringBuilder content = new StringBuilder();
    if (existingSummary != null && !existingSummary.isBlank()) {
      content.append("已有摘要：\n").append(existingSummary).append("\n\n");
    }
    content.append("以下为新对话内容，请把关键信息合并进摘要：\n");
    for (Message m : toFold) {
      content.append("用户：").append(m.getUserMessage()).append("\n");
      if (m.getAiMessage() != null && !m.getAiMessage().isBlank()) {
        content.append("助手：").append(m.getAiMessage()).append("\n");
      }
    }
    return content.toString();
  }
}
