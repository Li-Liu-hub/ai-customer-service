package com.knowledgeagent.ai.model;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * 多级降级聊天模型：按顺序依次尝试各级模型，当前级调用失败（超时/连接失败/非正常响应/空响应）
 * 时自动降级到下一级；全部失败时抛出最后一个异常。每级自身通过HTTP超时保证不会无限阻塞。
 */
@Slf4j
public class ResilientChatModel implements ChatModel {

  /** 按优先级排列的各级模型（至少一级才能正常提供服务）。 */
  private final List<ChatModel> delegates;

  /**
   * @param delegates 按优先级排列的模型列表
   */
  public ResilientChatModel(List<ChatModel> delegates) {
    this.delegates = delegates == null ? List.of() : List.copyOf(delegates);
  }

  /**
   * 依次尝试各级模型完成一次调用：正常返回且含有效文本的响应直接返回，
   * 失败则降级到下一级；全部失败时抛出最后一次的失败原因。
   *
   * @param prompt 聊天请求
   * @return 某一级模型返回的有效响应
   * @throws RuntimeException 全部级别均失败时抛出最后一次的异常
   */
  @Override
  public ChatResponse call(Prompt prompt) {
    RuntimeException lastFailure = null;
    for (int index = 0; index < delegates.size(); index++) {
      try {
        ChatResponse response = delegates.get(index).call(prompt);
        if (hasText(response)) {
          if (index > 0) {
            log.warn("聊天模型已降级至第{}级并完成本轮调用", index + 1);
          }
          return response;
        }
        lastFailure = new IllegalStateException("聊天模型返回了空响应");
        log.warn("第{}级聊天模型返回空响应，降级到下一级", index + 1);
      } catch (RuntimeException e) {
        lastFailure = e;
        log.warn("第{}级聊天模型调用失败，降级到下一级：{}", index + 1, e.getMessage());
      }
    }
    throw lastFailure != null ? lastFailure : new IllegalStateException("未配置任何聊天模型");
  }

  /**
   * 流式调用：优先使用第一级，仅在错误发生且后续级别存在时降级重试（已输出部分内容的场景降级语义有限）。
   *
   * @param prompt 聊天请求
   * @return 响应流
   */
  @Override
  public Flux<ChatResponse> stream(Prompt prompt) {
    if (delegates.isEmpty()) {
      return Flux.error(new IllegalStateException("未配置任何聊天模型"));
    }
    Flux<ChatResponse> flux = delegates.get(0).stream(prompt);
    for (int index = 1; index < delegates.size(); index++) {
      int level = index;
      flux =
          flux.onErrorResume(
              e -> {
                log.warn("第{}级聊天模型流式调用失败，降级到下一级：{}", level, e.getMessage());
                return delegates.get(level).stream(prompt);
              });
    }
    return flux;
  }

  @Override
  public ChatOptions getDefaultOptions() {
    return delegates.isEmpty() ? null : delegates.get(0).getDefaultOptions();
  }

  /**
   * 判断响应是否为含有效文本的正常响应。
   *
   * @param response 模型响应
   * @return 结果非空且文本非空白时返回true
   */
  private boolean hasText(ChatResponse response) {
    return response != null
        && response.getResult() != null
        && response.getResult().getOutput() != null
        && response.getResult().getOutput().getText() != null
        && !response.getResult().getOutput().getText().isBlank();
  }
}
