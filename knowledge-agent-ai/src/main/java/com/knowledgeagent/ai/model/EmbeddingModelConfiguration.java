package com.knowledgeagent.ai.model;

import com.knowledgeagent.ai.model.EmbeddingLevelProperties.EmbeddingLevel;
import com.knowledgeagent.common.config.EmbeddingProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

/**
 * 向量模型装配：按 app.ai.embedding.levels 依次构建各级 OpenAI 兼容向量模型，包成多级降级链
 * （级内不重试，故障立即降级下一级；每级带连接/读取超时保证不会无限阻塞）。
 */
@Slf4j
@Configuration
public class EmbeddingModelConfiguration {

  /** 本地/兼容服务不校验密钥时使用的占位密钥。 */
  private static final String PLACEHOLDER_API_KEY = "not-needed";

  /**
   * 创建多级降级向量模型：配置完整的级别按顺序参与降级链，维度取自入库规格配置。
   *
   * @param properties 降级链配置
   * @param embeddingProperties 入库规格配置（维度与模型名）
   * @return 应用统一的 EmbeddingModel（检索与入库共用）
   */
  @Bean
  public EmbeddingModel embeddingModel(
      EmbeddingLevelProperties properties, EmbeddingProperties embeddingProperties) {
    List<EmbeddingModel> delegates = new ArrayList<>();
    for (EmbeddingLevel level : properties.levels()) {
      if (!level.isConfigured()) {
        log.warn("向量模型级别[{}]未完整配置（需baseUrl与model），已跳过", level.name());
        continue;
      }
      delegates.add(buildLevelModel(level));
      log.info(
          "向量模型级别[{}]装配完成：{} @ {}（连接超时{}，读取超时{}）",
          level.name(),
          level.model(),
          level.baseUrl(),
          level.connectTimeout(),
          level.readTimeout());
    }
    if (delegates.isEmpty()) {
      log.error("未装配任何向量模型：检索与入库链路不可用");
    } else {
      log.info("向量模型降级链就绪，共{}级（维度{}）", delegates.size(), embeddingProperties.dimensions());
    }
    return new ResilientEmbeddingModel(delegates, embeddingProperties.dimensions());
  }

  /**
   * 构建单个级别的 OpenAI 兼容向量模型，带独立超时且不做级内重试。
   *
   * @param level 级别配置
   * @return 该级别的向量模型
   */
  private EmbeddingModel buildLevelModel(EmbeddingLevel level) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(level.connectTimeout());
    requestFactory.setReadTimeout(level.readTimeout());
    OpenAiApi api =
        OpenAiApi.builder()
            .baseUrl(level.baseUrl())
            .apiKey(level.apiKey() == null || level.apiKey().isBlank()
                ? PLACEHOLDER_API_KEY
                : level.apiKey())
            .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
            .build();
    RetryTemplate noRetryTemplate = new RetryTemplate();
    noRetryTemplate.setRetryPolicy(new NeverRetryPolicy());
    return new OpenAiEmbeddingModel(
        api,
        MetadataMode.EMBED,
        OpenAiEmbeddingOptions.builder().model(level.model()).build(),
        noRetryTemplate);
  }
}
