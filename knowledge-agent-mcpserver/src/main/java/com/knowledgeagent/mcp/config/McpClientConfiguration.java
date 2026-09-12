package com.knowledgeagent.mcp.config;

import com.knowledgeagent.mcp.support.ConnectionHeaderRequestCustomizer;
import com.knowledgeagent.mcp.support.ConnectionPrefixedToolNameGenerator;
import com.knowledgeagent.mcp.support.ExternalToolProvider;
import com.knowledgeagent.mcp.support.WhitelistToolFilter;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** MCP 接入层装配：外部工具提供者（白名单+来源前缀+容错）、连接身份命名与鉴权请求头定制。 */
@Configuration
public class McpClientConfiguration {

  /**
   * 创建外部工具提供者：聚合全部外部连接，按白名单过滤、加连接名前缀，
   * 并在外部服务不可用时安全降级为空工具列表。
   *
   * @param mcpSyncClients 外部MCP连接（未配置连接时为空）
   * @param properties 接入层配置（白名单、冷却时长）
   * @return 外部工具提供者
   */
  @Bean
  public ExternalToolProvider externalToolProvider(
      ObjectProvider<List<McpSyncClient>> mcpSyncClients, McpClientProperties properties) {
    return new ExternalToolProvider(
        mcpSyncClients.getIfAvailable(List::of),
        new WhitelistToolFilter(properties.toolWhitelist()),
        new ConnectionPrefixedToolNameGenerator(),
        properties.retryCooldown());
  }

  /**
   * 把每个连接的客户端身份名覆盖为连接名，供工具名前缀生成器标识工具来源。
   *
   * @param commonProperties 客户端公共配置（取版本号）
   * @return 连接身份定制器
   */
  @Bean
  public McpSyncClientCustomizer connectionNamedClientCustomizer(
      McpClientCommonProperties commonProperties) {
    return (connectionName, spec) ->
        spec.clientInfo(new McpSchema.Implementation(connectionName, commonProperties.getVersion()));
  }

  /**
   * 为配置了鉴权头的连接附加 HTTP 请求头（如 Bearer Token）。
   *
   * @param properties 接入层配置（连接请求头）
   * @param connections Streamable HTTP 连接配置（连接名到 url 的映射）
   * @return HTTP 请求定制器
   */
  @Bean
  public McpSyncHttpClientRequestCustomizer connectionHeaderRequestCustomizer(
      McpClientProperties properties, McpStreamableHttpClientProperties connections) {
    return new ConnectionHeaderRequestCustomizer(properties.connectionHeaders(), connections);
  }
}
