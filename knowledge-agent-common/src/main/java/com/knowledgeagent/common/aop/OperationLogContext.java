package com.knowledgeagent.common.aop;

import java.util.ArrayDeque;
import java.util.Deque;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/** 在一次被记录的业务操作中累计大模型名称和Token消耗。 */
public final class OperationLogContext {

  /** 保存当前线程中按调用层级排列的模型统计。 */
  private static final ThreadLocal<Deque<MutableMetrics>> METRICS_STACK =
      ThreadLocal.withInitial(ArrayDeque::new);

  private OperationLogContext() {}

  /** 初始化当前业务操作的模型统计。 */
  public static void start() {
    METRICS_STACK.get().push(new MutableMetrics());
  }

  /**
   * 累计一次Spring AI响应中的模型名称和Token数量。
   *
   * @param response Spring AI返回的聊天响应；为空时忽略
   */
  public static void add(ChatResponse response) {
    if (response == null || response.getMetadata() == null) {
      return;
    }
    Deque<MutableMetrics> stack = METRICS_STACK.get();
    if (stack.isEmpty()) {
      return;
    }
    MutableMetrics metrics = stack.peek();
    metrics.modelName = response.getMetadata().getModel();
    Usage usage = response.getMetadata().getUsage();
    if (usage != null) {
      metrics.promptTokens += valueOrZero(usage.getPromptTokens());
      metrics.completionTokens += valueOrZero(usage.getCompletionTokens());
    }
  }

  /**
   * 返回当前业务操作已经累计的模型统计。
   *
   * @return 不可变的模型名称和Token统计
   */
  public static ModelMetrics snapshot() {
    Deque<MutableMetrics> stack = METRICS_STACK.get();
    if (stack.isEmpty()) {
      return new ModelMetrics(null, null, null);
    }
    MutableMetrics metrics = stack.peek();
    Integer promptTokens = metrics.promptTokens == 0 ? null : metrics.promptTokens;
    Integer completionTokens = metrics.completionTokens == 0 ? null : metrics.completionTokens;
    return new ModelMetrics(metrics.modelName, promptTokens, completionTokens);
  }

  /** 清除当前线程的模型统计，避免线程复用造成数据串联。 */
  public static void clear() {
    Deque<MutableMetrics> stack = METRICS_STACK.get();
    if (!stack.isEmpty()) {
      stack.pop();
    }
    if (stack.isEmpty()) {
      METRICS_STACK.remove();
    }
  }

  /**
   * 把可能为空的Token数量转换为可累计整数。
   *
   * @param value 模型返回的Token数量
   * @return 原始值或0
   */
  private static int valueOrZero(Integer value) {
    return value == null ? 0 : value;
  }

  /** 保存一次业务操作累计的大模型调用信息。 */
  public record ModelMetrics(
      String modelName, Integer promptTokens, Integer completionTokens) {}

  /** 保存当前线程中可以继续累加的模型调用信息。 */
  private static final class MutableMetrics {
    private String modelName;
    private int promptTokens;
    private int completionTokens;
  }
}
