package com.knowledgeagent.ai.service.impl;

import com.knowledgeagent.ai.pojo.dto.BasicChatRequest;
import com.knowledgeagent.ai.pojo.dto.BasicChatTurn;
import com.knowledgeagent.ai.service.BasicChatService;
import com.knowledgeagent.common.exception.error.ConversationError;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

/** 最基本对话服务的实现，直接调用基本对话Agent完成多轮问答。 */
@Service
@RequiredArgsConstructor
public class BasicChatServiceImpl implements BasicChatService {

  /** 不携带任何工具的基本对话Agent。 */
  private final ChatClient basicChatAgent;

  @Override
  public String chat(BasicChatRequest request) {
    List<Message> messages = new ArrayList<>();
    if (request.getHistory() != null) {
      for (BasicChatTurn turn : request.getHistory()) {
        messages.add(toMessage(turn));
      }
    }
    messages.add(new UserMessage(request.getMessage()));

    String answer =
        basicChatAgent.prompt().messages(messages.toArray(Message[]::new)).call().content();
    if (answer == null || answer.isBlank()) {
      throw ConversationError.AI_RESPONSE_EMPTY.exception();
    }
    return answer;
  }

  /**
   * 把请求里的一个历史轮次转换为Spring AI消息。
   *
   * @param turn 历史轮次
   * @return 转换后的消息对象
   */
  private Message toMessage(BasicChatTurn turn) {
    if ("assistant".equalsIgnoreCase(turn.getRole())) {
      return new AssistantMessage(turn.getContent());
    }
    return new UserMessage(turn.getContent());
  }
}