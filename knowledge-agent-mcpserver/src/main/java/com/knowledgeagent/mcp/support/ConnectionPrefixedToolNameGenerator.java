package com.knowledgeagent.mcp.support;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.McpConnectionInfo;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;

/**
 * 用工具来源前缀命名外部工具（格式：来源__工具名），使不同 server 的同名工具互不覆盖，
 * 也让工具名自带来源信息便于模型选择。来源优先取连接名（接入层把连接的客户端身份名覆盖为连接名），
 * 缺失时依次回退到 server 自报名与固定值。
 */
public class ConnectionPrefixedToolNameGenerator implements McpToolNamePrefixGenerator {

  /** 来源名最大保留长度，避免工具名过长。 */
  private static final int MAX_SOURCE_LENGTH = 32;

  /**
   * 生成带来源前缀的工具名。
   *
   * @param connectionInfo 工具所属连接的信息
   * @param tool 工具（原始名）
   * @return 格式为「来源__原始工具名」的规范化名称
   */
  @Override
  public String prefixedToolName(McpConnectionInfo connectionInfo, McpSchema.Tool tool) {
    return resolveSourceName(connectionInfo) + "__" + sanitize(tool.name());
  }

  /**
   * 解析工具来源名：连接名（客户端身份名）优先，其次 server 自报名，最后回退固定值。
   *
   * @param connectionInfo 连接信息，可能为 null
   * @return 规范化并截断后的来源名
   */
  private String resolveSourceName(McpConnectionInfo connectionInfo) {
    if (connectionInfo != null
        && connectionInfo.clientInfo() != null
        && isPresent(connectionInfo.clientInfo().name())) {
      return limitLength(sanitize(connectionInfo.clientInfo().name()));
    }
    if (connectionInfo != null
        && connectionInfo.initializeResult() != null
        && connectionInfo.initializeResult().serverInfo() != null
        && isPresent(connectionInfo.initializeResult().serverInfo().name())) {
      return limitLength(sanitize(connectionInfo.initializeResult().serverInfo().name()));
    }
    return "mcp";
  }

  /**
   * 判断名称是否有效（非 null 且非空白）。
   *
   * @param name 名称
   * @return 有效时返回 true
   */
  private boolean isPresent(String name) {
    return name != null && !name.isBlank();
  }

  /**
   * 把名称规范化为 MCP 工具名允许的字符集（字母、数字、下划线、连字符）。
   *
   * @param name 原始名称
   * @return 规范化后的名称
   */
  private String sanitize(String name) {
    return name.strip().replaceAll("[^a-zA-Z0-9_-]", "-");
  }

  /**
   * 截断来源名到最大长度。
   *
   * @param name 来源名
   * @return 不超长的名称
   */
  private String limitLength(String name) {
    return name.length() > MAX_SOURCE_LENGTH ? name.substring(0, MAX_SOURCE_LENGTH) : name;
  }
}
