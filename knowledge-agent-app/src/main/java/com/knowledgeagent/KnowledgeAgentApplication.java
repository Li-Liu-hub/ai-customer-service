package com.knowledgeagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Knowledge Agent 后端的唯一启动入口。 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class KnowledgeAgentApplication {
  /**
   * 启动 REST、MCP 和后台处理能力所在的 Spring Boot 应用。
   *
   * @param args 命令行启动参数
   */
  public static void main(String[] args) {
    SpringApplication.run(KnowledgeAgentApplication.class, args);
  }
}
