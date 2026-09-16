package com.knowledgeagent.mcp.support;

import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/** 外部工具回调的容错装饰器：执行失败时返回失败说明文本，让对话链路继续而不是整轮报错。 */
@Slf4j
public class SafeToolCallback implements ToolCallback {

  /** 被装饰的外部工具回调。 */
  private final ToolCallback delegate;

  /**
   * @param delegate 外部工具回调
   */
  public SafeToolCallback(ToolCallback delegate) {
    this.delegate = delegate;
  }

  @Override
  public ToolDefinition getToolDefinition() {
    return delegate.getToolDefinition();
  }

  @Override
  public ToolMetadata getToolMetadata() {
    return delegate.getToolMetadata();
  }

  @Override
  public String call(String toolInput) {
    return safeCall(() -> delegate.call(toolInput));
  }

  @Override
  public String call(String toolInput, ToolContext toolContext) {
    return safeCall(() -> delegate.call(toolInput, toolContext));
  }

  /**
   * 执行外部调用并把异常转换为模型可理解的失败说明。
   *
   * @param call 实际的外部工具调用
   * @return 工具正常返回时原样返回；失败时返回失败说明文本
   */
  private String safeCall(Supplier<String> call) {
    long startMillis = System.currentTimeMillis();
    try {
      String result = call.get();
      log.info(
          "外部MCP工具调用完成 tool={} 耗时{}ms",
          delegate.getToolDefinition().name(),
          System.currentTimeMillis() - startMillis);
      return result;
    } catch (Exception e) {
      log.warn(
          "外部MCP工具调用失败 tool={} 耗时{}ms reason={}",
          delegate.getToolDefinition().name(),
          System.currentTimeMillis() - startMillis,
          e.getMessage());
      return "外部工具执行失败（外部服务暂时不可用），请如实告知用户稍后重试，不要编造工具执行结果。";
    }
  }
}
