package com.knowledgeagent;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置：定制 OpenAPI 元信息（Apifox/Swagger 导入时展示的项目名、版本与描述）。
 * 注：springdoc 的 api-docs.* 配置项不包含元信息，需以 OpenAPI Bean 方式定制。
 */
@Configuration
public class OpenApiConfiguration {

  /**
   * 构建 OpenAPI 文档元信息。
   *
   * @return OpenAPI 实例（标题/版本/描述）
   */
  @Bean
  public OpenAPI knowledgeAgentOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("knowledge-agent 智能客服接口")
                .version("0.1.0")
                .description("电商智能客服 Agent：知识库管理与检索、多轮对话、会话查询、日志查询"));
  }
}
