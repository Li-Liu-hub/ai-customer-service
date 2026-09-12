package com.knowledgeagent.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 保存知识分块向量化时需要的模型参数。
 *
 * @param modelName 保存到文件记录中的向量模型名称
 * @param dimensions 向量模型必须返回的向量维度
 * @param batchSize 单次发送给向量模型的最大切片数量
 */
@ConfigurationProperties("app.embedding")
public record EmbeddingProperties(String modelName, int dimensions, int batchSize) {}
