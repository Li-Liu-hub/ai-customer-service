package com.knowledgeagent.mcp.mock;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * mock 外部 MCP 服务的订单工具：一个用于验证白名单放行，另一个用于验证白名单过滤。
 * 服务端工具必须使用 @McpTool（springaicommunity 提供）：Spring AI 服务端注解扫描器只识别
 * @McpTool/@McpResource/@McpPrompt/@McpComplete，不识别客户端工具注解 @Tool。
 */
@Component
public class MockOrderTools {

  /**
   * 查询订单最新状态。
   *
   * @param orderId 订单号
   * @return 订单状态描述
   */
  @McpTool(description = "查询订单的最新状态")
  public String queryOrderStatus(@McpToolParam(description = "订单号") String orderId) {
    return "订单 " + orderId + " 状态：已发货（mock 数据）";
  }

  /**
   * 查询订单物流轨迹。
   *
   * @param orderId 订单号
   * @return 物流轨迹描述
   */
  @McpTool(description = "查询订单的物流轨迹")
  public String getLogisticsTrace(@McpToolParam(description = "订单号") String orderId) {
    return "订单 " + orderId + " 物流：已到达分发中心（mock 数据）";
  }
}
