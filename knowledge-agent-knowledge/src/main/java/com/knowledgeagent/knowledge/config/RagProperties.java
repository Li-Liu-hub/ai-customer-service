package com.knowledgeagent.knowledge.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG检索链路配置：控制检索默认参数与各调优开关（默认全关，即最粗糙基线）。
 *
 * @param defaultTopK 检索默认返回分块数
 * @param maxTopK 检索允许的最大返回分块数
 * @param oversampleFactor HNSW候选放大倍数（抵消知识库过滤损耗）
 * @param queryRewriteEnabled 是否启用大模型问题重写
 * @param hybridSearchEnabled 是否启用向量+关键词混合检索（RRF融合）
 * @param rerankEnabled 是否启用专用重排模型结果重排
 * @param rerankCandidates 送入重排的候选数量
 * @param rerankBaseUrl 重排服务地址（OpenAI兼容 /v1/rerank 接口）
 * @param rerankModelName 重排模型名称
 * @param rerankTimeout 重排服务读取超时（连接超时固定5秒）
 */
@ConfigurationProperties("app.rag")
public record RagProperties(
    int defaultTopK,
    int maxTopK,
    int oversampleFactor,
    boolean queryRewriteEnabled,
    boolean hybridSearchEnabled,
    boolean rerankEnabled,
    int rerankCandidates,
    String rerankBaseUrl,
    String rerankModelName,
    Duration rerankTimeout) {

  /** 归一化配置：重排超时缺省时使用30秒。 */
  public RagProperties {
    rerankTimeout = rerankTimeout == null ? Duration.ofSeconds(30) : rerankTimeout;
  }
}
