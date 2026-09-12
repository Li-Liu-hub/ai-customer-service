package com.knowledgeagent.mcp.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knowledgeagent.mcp.support.ExternalToolProvider;
import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 接入层故障容错测试：外部 MCP 服务不可用时（连接被拒绝），
 * 应用应正常启动、工具获取安全降级为空，且不反复尝试连接拖垮对话链路。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_MCP_TEST", matches = "true")
class McpClientFailureToleranceTest {

  /** 无服务监听的端口，-1 表示尚未确定。 */
  private static volatile int closedPort = -1;

  /** 接入层外部工具提供者。 */
  @Autowired private ExternalToolProvider externalToolProvider;

  /** 把客户端连接指向一个没有任何服务监听的端口。 */
  @DynamicPropertySource
  static void mcpProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.ai.mcp.client.streamable-http.connections.down.url",
        () -> "http://127.0.0.1:" + closedPort());
  }

  /**
   * 申请一个随后立即释放的空闲端口，保证该端口没有任何服务监听。
   *
   * @return 无监听的端口
   */
  private static synchronized int closedPort() {
    if (closedPort < 0) {
      try (ServerSocket socket = new ServerSocket(0)) {
        closedPort = socket.getLocalPort();
      } catch (IOException e) {
        throw new IllegalStateException("无法获取空闲端口", e);
      }
    }
    return closedPort;
  }

  /** 外部服务不可用时应用能正常启动（本测试能运行即证明）且工具安全降级为空。 */
  @Test
  void toolsDegradeToEmptyWhenServerUnreachable() {
    long start = System.nanoTime();
    ToolCallback[] callbacks = externalToolProvider.toolCallbacks();
    long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

    assertArrayEquals(new ToolCallback[0], callbacks, "外部服务不可用时工具应降级为空");
    assertTrue(elapsedMillis < 10_000, "连接被拒绝应快速失败，不应长时间阻塞对话");
  }

  /** 失败后的冷却期内不应再次尝试连接，应直接快速返回空。 */
  @Test
  void cooldownAvoidsRepeatedConnectAttempts() {
    externalToolProvider.toolCallbacks();
    long start = System.nanoTime();
    ToolCallback[] callbacks = externalToolProvider.toolCallbacks();
    long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

    assertArrayEquals(new ToolCallback[0], callbacks);
    assertTrue(elapsedMillis < 100, "冷却期内应直接返回空，不再发起连接");
  }
}
