package com.knowledgeagent.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 保存文本切片使用的字符长度配置。
 *
 * @param targetLength 单个切片期望包含的字符数量
 * @param maxLength 单个切片允许包含的最大字符数量
 * @param overlapLength 相邻切片重复保留的字符数量
 */
@ConfigurationProperties("app.text-chunk")
public record TextChunkProperties(int targetLength, int maxLength, int overlapLength) {}
