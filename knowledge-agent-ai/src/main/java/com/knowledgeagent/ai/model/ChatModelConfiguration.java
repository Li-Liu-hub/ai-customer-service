package com.knowledgeagent.ai.model;

import com.knowledgeagent.ai.model.ChatLevelProperties.ChatLevel;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

/**
 * 聊天模型装配：按 app.ai.chat.levels 依次构建各级 OpenAI 兼容模型，包成多级降级链
 * （级内不重试，故障立即降级下一级；每级带连接/读取超时保证不会无限阻塞）。
 */
@Slf4j
@Configuration
public class ChatModelConfiguration {

  /** 本地/兼容服务不校验密钥时使用的占位密钥。 */
  private static final String PLACEHOLDER_API_KEY = "not-needed";

  /**
   * 创建多级降级聊天模型：配置完整的级别按顺序参与降级链。
   *
   * @param properties 降级链配置
   * @return 应用统一的 ChatModel（所有对话、摘要、标题、重写共用）
   */
  @Bean
  public ChatModel chatModel(ChatLevelProperties properties) {
    List<ChatModel> delegates = new ArrayList<>();
    for (ChatLevel level : properties.levels()) {
      if (!level.isConfigured()) {
        log.warn("聊天模型级别[{}]未完整配置（需baseUrl与model），已跳过", level.name());
        continue;
      }
      delegates.add(buildLevelModel(level));
      log.info(
          "聊天模型级别[{}]装配完成：{} @ {}（连接超时{}，读取超时{}）",
          level.name(),
          level.model(),
          level.baseUrl(),
          level.connectTimeout(),
          level.readTimeout());
    }
    if (delegates.isEmpty()) {
      log.error("未装配任何聊天模型：对话链路不可用");
    } else {
      log.info("聊天模型降级链就绪，共{}级", delegates.size());
    }
    return new ResilientChatModel(delegates);
  }

  /**
   * 构建单个级别的 OpenAI 兼容聊天模型，带独立超时且不做级内重试。
   *
   * @param level 级别配置
   * @return 该级别的聊天模型
   */
  private ChatModel buildLevelModel(ChatLevel level) {
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
    return OpenAiChatModel.builder()
        .openAiApi(api)
        .defaultOptions(OpenAiChatOptions.builder().model(level.model()).build())
        .retryTemplate(noRetryTemplate)
        .build();
  }
}
