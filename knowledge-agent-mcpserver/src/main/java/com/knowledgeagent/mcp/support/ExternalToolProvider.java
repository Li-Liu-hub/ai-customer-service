package com.knowledgeagent.mcp.support;

import io.modelcontextprotocol.client.McpSyncClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.McpToolFilter;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.ai.mcp.McpToolsChangedEvent;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.ApplicationListener;

/**
 * 外部 MCP 工具提供者：编排外部连接的初始化与工具获取，并保证外部服务故障不拖垮对话链路。
 * 刻意不实现 ToolCallbackProvider 接口，避免被 /mcp 服务端自动收集后把外部工具反向对外暴露。
 */
@Slf4j
public class ExternalToolProvider implements ApplicationListener<McpToolsChangedEvent> {

  /** 无外部工具时共享的空数组。 */
  private static final ToolCallback[] NO_TOOL_CALLBACKS = new ToolCallback[0];

  /** 外部 MCP 连接（由自动配置按 streamable-http 连接参数创建，未配置时为空）。 */
  private final List<McpSyncClient> clients;

  /** 白名单过滤与来源前缀命名后的工具回调来源。 */
  private final SyncMcpToolCallbackProvider delegate;

  /** 获取工具失败后的重试冷却时长。 */
  private final Duration retryCooldown;

  /** 冷却截止时间戳（epoch 毫秒），到点前不再尝试连接外部服务。 */
  private final AtomicLong retryAfterMillis = new AtomicLong(0);

  /** 串行化初始化与工具获取，避免并发对话同时连接外部服务。 */
  private final ReentrantLock lock = new ReentrantLock();

  /**
   * @param clients 外部 MCP 连接，可为空列表
   * @param toolFilter 工具过滤器（原始工具名白名单）
   * @param toolNamePrefixGenerator 工具名前缀生成器（按连接名生成来源前缀）
   * @param retryCooldown 获取失败后的重试冷却时长
   */
  public ExternalToolProvider(
      List<McpSyncClient> clients,
      McpToolFilter toolFilter,
      McpToolNamePrefixGenerator toolNamePrefixGenerator,
      Duration retryCooldown) {
    this.clients = clients == null ? List.of() : List.copyOf(clients);
    this.delegate =
        new SyncMcpToolCallbackProvider(
            toolFilter, toolNamePrefixGenerator, this.clients.toArray(McpSyncClient[]::new));
    this.retryCooldown = retryCooldown;
  }

  /**
   * 获取当前可用的外部工具：确保外部连接已初始化，按白名单过滤并加来源前缀后返回。
   * 外部服务不可用时返回空数组并进入冷却期，不向调用方抛出异常。
   *
   * @return 外部工具回调数组；无连接配置或外部服务不可用时为空数组
   */
  public ToolCallback[] toolCallbacks() {
    if (clients.isEmpty() || isInCooldown()) {
      return NO_TOOL_CALLBACKS;
    }
    lock.lock();
    try {
      if (isInCooldown()) {
        return NO_TOOL_CALLBACKS;
      }
      initializeConnections();
      ToolCallback[] callbacks = delegate.getToolCallbacks();
      retryAfterMillis.set(0);
      return Arrays.stream(callbacks).map(SafeToolCallback::new).toArray(ToolCallback[]::new);
    } catch (Exception e) {
      retryAfterMillis.set(System.currentTimeMillis() + retryCooldown.toMillis());
      log.warn("外部MCP工具获取失败，{}内暂不重试：{}", retryCooldown, e.getMessage());
      return NO_TOOL_CALLBACKS;
    } finally {
      lock.unlock();
    }
  }

  /**
   * 工具清单变化时转发事件，使内部工具缓存失效并在下次获取时重新拉取。
   *
   * @param event 外部工具变更事件
   */
  @Override
  public void onApplicationEvent(McpToolsChangedEvent event) {
    delegate.onApplicationEvent(event);
  }

  /**
   * 判断是否处于失败冷却期。
   *
   * @return 冷却期内返回 true
   */
  private boolean isInCooldown() {
    return System.currentTimeMillis() < retryAfterMillis.get();
  }

  /**
   * 初始化尚未就绪的外部连接（已初始化的连接直接跳过）。
   */
  private void initializeConnections() {
    for (McpSyncClient client : clients) {
      if (!client.isInitialized()) {
        client.initialize();
      }
    }
  }
}
