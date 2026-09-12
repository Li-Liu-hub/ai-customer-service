package com.knowledgeagent.mcp.mock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 独立的 mock 外部 MCP 服务（Streamable HTTP）：供接入层集成测试作为真实外部依赖使用。
 * 仅扫描本包并排除数据源/缓存/消息队列自动配置，避免加载主应用的业务组件。
 * 该类位于主应用组件扫描范围内，必须用条件开关隔离：否则会作为普通配置类混入主应用的
 * 测试上下文，使其 exclude 与扫描范围污染主应用。
 */
@ConditionalOnProperty(name = "mock.mcp.server.enabled", havingValue = "true")
@SpringBootApplication(
    scanBasePackages = "com.knowledgeagent.mcp.mock",
    exclude = {
      DataSourceAutoConfiguration.class,
      RedisAutoConfiguration.class,
      RabbitAutoConfiguration.class
    })
public class MockMcpServerApplication {

  /**
   * 启动 mock 服务。
   *
   * @param args 启动参数
   */
  public static void main(String[] args) {
    SpringApplication.run(MockMcpServerApplication.class, args);
  }
}
