package com.knowledgeagent.mcp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * /mcp 服务端工具暴露测试：连接本应用自身的 /mcp 端点，验证知识库服务端工具
 * （@McpTool 注解式）已对外暴露，且不存在意外泄漏的客户端工具。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "RUN_MCP_TEST", matches = "true")
class McpServerToolExposureTest {

  /** 应用随机监听端口。 */
  @LocalServerPort private int port;

  /** 应用统一JSON映射器。 */
  @Autowired private ObjectMapper objectMapper;

  /** /mcp 应暴露知识库工具且仅有两个，不泄漏客户端工具。 */
  @Test
  void exposesKnowledgeToolsAndNoLeakedClientTools() {
    HttpClientStreamableHttpTransport transport =
        HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
            .endpoint("/mcp")
            .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
            .build();
    try (McpSyncClient client =
        McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).build()) {
      client.initialize();
      Set<String> toolNames =
          client.listTools().tools().stream()
              .map(McpSchema.Tool::name)
              .collect(Collectors.toSet());

      assertTrue(toolNames.contains("query_knowledge_bases"), "应暴露知识库枚举工具");
      assertTrue(toolNames.contains("search_knowledge"), "应暴露知识库检索工具");
      assertEquals(2, toolNames.size(), "不应存在其他（意外泄漏的）工具");
    }
  }
}
