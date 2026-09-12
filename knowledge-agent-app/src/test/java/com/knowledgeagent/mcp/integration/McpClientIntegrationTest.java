package com.knowledgeagent.mcp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knowledgeagent.mcp.mock.MockMcpServerApplication;
import com.knowledgeagent.mcp.support.ExternalToolProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * MCP 接入层全链路集成测试：启动一个真实的 mock 外部 MCP 服务（Streamable HTTP），
 * 验证白名单过滤、连接名前缀与工具调用贯通。需要本机端口可用，不依赖任何外部系统。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_MCP_TEST", matches = "true")
class McpClientIntegrationTest {

  /** mock 外部 MCP 服务上下文（惰性启动，仅在动态属性求值时创建）。 */
  private static volatile ConfigurableApplicationContext mockServer;

  /** mock 服务端口，-1 表示尚未启动。 */
  private static volatile int mockServerPort = -1;

  /** 接入层外部工具提供者。 */
  @Autowired private ExternalToolProvider externalToolProvider;

  /** 把客户端连接指向 mock 服务，并把白名单收窄到单个工具（另一个工具应被过滤）。 */
  @DynamicPropertySource
  static void mcpProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.ai.mcp.client.streamable-http.connections.mock.url",
        () -> "http://127.0.0.1:" + ensureMockServer());
    registry.add("app.mcp.tool-whitelist[0]", () -> "queryOrderStatus");
  }

  /**
   * 惰性启动 mock 外部 MCP 服务并返回端口。
   *
   * @return mock 服务监听端口
   */
  private static synchronized int ensureMockServer() {
    if (mockServerPort < 0) {
      mockServer =
          new SpringApplicationBuilder(MockMcpServerApplication.class)
              .properties(
                  "mock.mcp.server.enabled=true",
                  "spring.ai.mcp.client.enabled=false",
                  "spring.ai.mcp.server.protocol=STATELESS",
                  "spring.ai.mcp.server.name=mock-order-service",
                  "spring.ai.mcp.server.version=0.1.0")
              // 端口与工具开关必须走命令行参数：SpringApplicationBuilder.properties 是最低优先级的
              // default properties，会被 classpath 的 application.yml（server.port、工具开关）覆盖
              .run("--server.port=0", "--spring.ai.mcp.server.tool-callback-converter=true");
      mockServerPort = ((ServletWebServerApplicationContext) mockServer).getWebServer().getPort();
    }
    return mockServerPort;
  }

  /** 测试结束后关闭 mock 服务。 */
  @AfterAll
  static void shutdownMockServer() {
    if (mockServer != null) {
      mockServer.close();
    }
  }

  /** 白名单过滤与连接名来源前缀应同时生效。 */
  @Test
  void whitelistFiltersAndConnectionNamePrefixesExternalTools() {
    ToolCallback[] callbacks = externalToolProvider.toolCallbacks();

    assertEquals(1, callbacks.length, "白名单应收窄到 queryOrderStatus 单个工具");
    assertEquals("mock__queryOrderStatus", callbacks[0].getToolDefinition().name());
  }

  /** 外部工具调用应完整贯通到 mock 服务并返回结果。 */
  @Test
  void externalToolCallReturnsMockResult() {
    ToolCallback[] callbacks = externalToolProvider.toolCallbacks();

    String result = callbacks[0].call("{\"orderId\":\"JD20260912\"}");

    assertTrue(result.contains("JD20260912"), "结果应包含订单号");
    assertTrue(result.contains("已发货"), "结果应包含 mock 返回的订单状态");
  }
}
