package com.knowledgeagent.mcp.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP 接入层配置：外部工具白名单、按连接的鉴权请求头与故障重试冷却。
 *
 * @param toolWhitelist 外部工具白名单（原始工具名，非加前缀后的名字）；为空时放行全部外部工具
 * @param connectionHeaders 按连接名附加的 HTTP 请求头，结构为 {连接名: {头名: 值}}
 * @param retryCooldown 外部服务不可用后的重试冷却时长，冷却期内直接返回空工具列表
 */
@ConfigurationProperties("app.mcp")
public record McpClientProperties(
    List<String> toolWhitelist,
    Map<String, Map<String, String>> connectionHeaders,
    Duration retryCooldown) {

  /** 归一化配置：缺省时使用安全默认值（不限制白名单、无附加请求头、冷却30秒）。 */
  public McpClientProperties {
    toolWhitelist = toolWhitelist == null ? List.of() : List.copyOf(toolWhitelist);
    Map<String, Map<String, String>> copiedHeaders = new LinkedHashMap<>();
    if (connectionHeaders != null) {
      connectionHeaders.forEach(
          (connectionName, headers) ->
              copiedHeaders.put(
                  connectionName, headers == null ? Map.of() : Map.copyOf(headers)));
    }
    connectionHeaders = Map.copyOf(copiedHeaders);
    retryCooldown = retryCooldown == null ? Duration.ofSeconds(30) : retryCooldown;
  }
}
