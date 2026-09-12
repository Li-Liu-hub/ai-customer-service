package com.knowledgeagent.ai.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** 多级降级聊天模型单测：验证调用顺序、空响应降级、全挂异常与未配置场景。 */
class ResilientChatModelTest {

  /** 第一级正常时直接返回第一级结果。 */
  @Test
  void usesFirstLevelWhenHealthy() {
    ChatResponse response =
        new ResilientChatModel(List.of(fixed("first"), fixed("second"))).call(new Prompt("hi"));

    assertEquals("first", response.getResult().getOutput().getText());
  }

  /** 第一级调用失败时自动降级到第二级。 */
  @Test
  void fallsBackToSecondWhenFirstFails() {
    ChatResponse response =
        new ResilientChatModel(List.of(failing("一级不可用"), fixed("second")))
            .call(new Prompt("hi"));

    assertEquals("second", response.getResult().getOutput().getText());
  }

  /** 第一级返回空响应时同样降级到下一级。 */
  @Test
  void fallsBackOnEmptyResponse() {
    ChatModel empty =
        prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(" "))));

    ChatResponse response =
        new ResilientChatModel(List.of(empty, fixed("second"))).call(new Prompt("hi"));

    assertEquals("second", response.getResult().getOutput().getText());
  }

  /** 全部级别失败时抛出最后一次的失败原因。 */
  @Test
  void throwsLastFailureWhenAllLevelsFail() {
    ResilientChatModel model =
        new ResilientChatModel(List.of(failing("一级挂了"), failing("二级挂了")));

    RuntimeException failure =
        assertThrows(RuntimeException.class, () -> model.call(new Prompt("hi")));
    assertTrue(failure.getMessage().contains("二级挂了"));
  }

  /** 未配置任何级别时抛出明确异常，不吞错。 */
  @Test
  void throwsWhenNoLevelsConfigured() {
    ResilientChatModel model = new ResilientChatModel(List.of());

    IllegalStateException failure =
        assertThrows(IllegalStateException.class, () -> model.call(new Prompt("hi")));
    assertTrue(failure.getMessage().contains("未配置任何聊天模型"));
  }

  /**
   * 构造固定返回文本的模型。
   *
   * @param text 返回文本
   * @return 固定响应模型
   */
  private ChatModel fixed(String text) {
    return prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  /**
   * 构造始终抛异常的模型。
   *
   * @param message 异常信息
   * @return 失败模型
   */
  private ChatModel failing(String message) {
    return prompt -> {
      throw new IllegalStateException(message);
    };
  }
}
