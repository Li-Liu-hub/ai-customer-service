package com.knowledgeagent.mcp.support;

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Map;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;

/**
 * 为指定连接附加 HTTP 请求头（如 Bearer 鉴权）：请求地址与各连接配置的 url 做前缀匹配，
 * 命中连接后把该连接配置的请求头写入本次请求。
 */
public class ConnectionHeaderRequestCustomizer implements McpSyncHttpClientRequestCustomizer {

  /** 按连接名配置的附加请求头。 */
  private final Map<String, Map<String, String>> headersByConnection;

  /** Streamable HTTP 连接配置，用于建立连接名到请求地址的映射。 */
  private final McpStreamableHttpClientProperties connections;

  /**
   * @param headersByConnection 按连接名配置的请求头
   * @param connections 连接配置（连接名到 url 的映射）
   */
  public ConnectionHeaderRequestCustomizer(
      Map<String, Map<String, String>> headersByConnection,
      McpStreamableHttpClientProperties connections) {
    this.headersByConnection = headersByConnection;
    this.connections = connections;
  }

  /**
   * 发送前给命中的连接请求附加配置的请求头。
   *
   * @param builder HTTP 请求构建器
   * @param method 请求方法
   * @param uri 请求地址
   * @param body 请求体，可能为空
   * @param context 传输上下文
   */
  @Override
  public void customize(
      HttpRequest.Builder builder, String method, URI uri, String body, McpTransportContext context) {
    if (headersByConnection.isEmpty() || connections.getConnections() == null) {
      return;
    }
    String target = uri.toString();
    for (Map.Entry<String, McpStreamableHttpClientProperties.ConnectionParameters> entry :
        connections.getConnections().entrySet()) {
      Map<String, String> headers = headersByConnection.get(entry.getKey());
      if (headers == null || headers.isEmpty()) {
        continue;
      }
      if (matchesConnection(entry.getValue().url(), target)) {
        headers.forEach(builder::header);
        return;
      }
    }
  }

  /**
   * 判断请求地址是否属于指定连接：按连接 url 前缀匹配，忽略末尾斜杠。
   *
   * @param connectionUrl 连接配置的 url
   * @param requestUri 请求地址
   * @return 属于该连接时返回 true
   */
  private boolean matchesConnection(String connectionUrl, String requestUri) {
    if (connectionUrl == null || connectionUrl.isBlank()) {
      return false;
    }
    String base =
        connectionUrl.endsWith("/")
            ? connectionUrl.substring(0, connectionUrl.length() - 1)
            : connectionUrl;
    return requestUri.equals(base)
        || requestUri.startsWith(base + "/")
        || requestUri.startsWith(base + "?");
  }
}
