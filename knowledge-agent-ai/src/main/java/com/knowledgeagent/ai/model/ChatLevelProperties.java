package com.knowledgeagent.ai.model;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 聊天模型降级链配置：按顺序排列的多级模型，前一级调用失败（超时/连接失败/空响应）时自动降级到下一级。
 *
 * @param levels 按优先级排列的模型级别列表
 */
@ConfigurationProperties("app.ai.chat")
public record ChatLevelProperties(List<ChatLevel> levels) {

  /** 归一化配置：缺省时为空列表（不装配任何模型）。 */
  public ChatLevelProperties {
    levels = levels == null ? List.of() : List.copyOf(levels);
  }

  /**
   * 单级聊天模型配置。
   *
   * @param name 级别名称（日志标识）
   * @param baseUrl OpenAI兼容服务地址
   * @param apiKey API密钥；本地服务可留空
   * @param model 模型名称
   * @param connectTimeout 连接超时
   * @param readTimeout 读取（响应）超时
   */
  public record ChatLevel(
      String name,
      String baseUrl,
      String apiKey,
      String model,
      Duration connectTimeout,
      Duration readTimeout) {

    /** 归一化配置：缺省时使用安全默认值。 */
    public ChatLevel {
      connectTimeout = connectTimeout == null ? Duration.ofSeconds(10) : connectTimeout;
      readTimeout = readTimeout == null ? Duration.ofSeconds(120) : readTimeout;
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
