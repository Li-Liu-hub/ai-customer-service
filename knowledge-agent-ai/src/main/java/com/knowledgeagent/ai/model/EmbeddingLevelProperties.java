package com.knowledgeagent.ai.model;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 向量模型降级链配置：按顺序排列的多级向量服务，前一级调用失败（超时/连接失败/维度不符）时
 * 自动降级到下一级。各级必须指向维度一致、向量空间一致的模型（如均为 bge-m3），否则向量不可比。
 *
 * @param levels 按优先级排列的向量模型级别列表
 */
@ConfigurationProperties("app.ai.embedding")
public record EmbeddingLevelProperties(List<EmbeddingLevel> levels) {

  /** 归一化配置：缺省时为空列表（不装配任何向量模型）。 */
  public EmbeddingLevelProperties {
    levels = levels == null ? List.of() : List.copyOf(levels);
  }

  /**
   * 单级向量模型配置。
   *
   * @param name 级别名称（日志标识）
   * @param baseUrl OpenAI兼容服务地址
   * @param apiKey API密钥；本地服务可留空
   * @param model 模型名称（各级必须为同维度同族模型）
   * @param connectTimeout 连接超时
   * @param readTimeout 读取（响应）超时
   */
  public record EmbeddingLevel(
      String name,
      String baseUrl,
      String apiKey,
      String model,
      Duration connectTimeout,
      Duration readTimeout) {

    /** 归一化配置：缺省时使用安全默认值。 */
    public EmbeddingLevel {
      connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
      readTimeout = readTimeout == null ? Duration.ofSeconds(60) : readTimeout;
    }

    /**
     * 判断该级别是否配置完整（可参与装配）。
     *
     * @return baseUrl与model均非空时返回true
     */
    public boolean isConfigured() {
      return baseUrl != null && !baseUrl.isBlank() && model != null && !model.isBlank();
    }
  }
}
