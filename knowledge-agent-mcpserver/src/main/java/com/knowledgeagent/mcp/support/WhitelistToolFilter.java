package com.knowledgeagent.mcp.support;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Set;
import org.springframework.ai.mcp.McpConnectionInfo;
import org.springframework.ai.mcp.McpToolFilter;

/** 按原始工具名白名单过滤外部工具；白名单为空时放行全部工具。 */
public class WhitelistToolFilter implements McpToolFilter {

  /** 放行的工具原始名集合（过滤发生在加连接名前缀之前）。 */
  private final Set<String> allowedToolNames;

  /**
   * @param allowedToolNames 放行的工具原始名；为空表示不限制
   */
  public WhitelistToolFilter(List<String> allowedToolNames) {
    this.allowedToolNames = allowedToolNames == null ? Set.of() : Set.copyOf(allowedToolNames);
  }

  /**
   * 判断指定工具是否放行。
   *
   * @param connectionInfo 工具所属连接的信息
   * @param tool 待判断的工具（使用 server 返回的原始名）
   * @return 白名单为空或工具在名单内时返回 true
   */
  @Override
  public boolean test(McpConnectionInfo connectionInfo, McpSchema.Tool tool) {
    return allowedToolNames.isEmpty() || allowedToolNames.contains(tool.name());
  }
}
