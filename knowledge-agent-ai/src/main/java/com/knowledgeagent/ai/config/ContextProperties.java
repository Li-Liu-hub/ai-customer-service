package com.knowledgeagent.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 多轮对话上下文预算配置：以模型上下文窗口为基准的token压缩策略（对齐codex机制，
 * 占窗口90%触发压缩，压缩后回落到窗口的15%-25%）。
 *
 * @param contextWindowTokens 模型上下文窗口（token），与本地llama.cpp启动参数-c保持一致
 * @param compactTriggerRatio 压缩触发比例（摘要+活跃历史占窗口达到该比例时触发）
 * @param compactTargetRatio 压缩后目标占比（新摘要+剩余活跃合计约占窗口的比例）
 */
@ConfigurationProperties("app.ai")
public record ContextProperties(
    int contextWindowTokens, double compactTriggerRatio, double compactTargetRatio) {}
