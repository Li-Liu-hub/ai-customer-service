package com.knowledgeagent.conversation.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 会话配置：标题长度上限与并发处理锁的僵尸超时。
 *
 * @param titleMaxLength 首轮会话标题允许保留的最大字符数
 * @param processingStaleTimeout 处理锁僵尸超时：一轮对话发起后超过该时长仍未完成，视为失效锁可被下一轮接管
 */
@ConfigurationProperties("app.conversation")
public record ConversationProperties(int titleMaxLength, Duration processingStaleTimeout) {

  /** 归一化配置：缺省时使用安全默认值。 */
  public ConversationProperties {
    titleMaxLength = titleMaxLength <= 0 ? 10 : titleMaxLength;
    processingStaleTimeout =
        processingStaleTimeout == null ? Duration.ofMinutes(10) : processingStaleTimeout;
  }
}
