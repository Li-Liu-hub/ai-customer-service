package com.knowledgeagent.ai.model;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * 多级降级向量模型：按顺序依次尝试各级向量服务，当前级调用失败（超时/连接失败/异常）
 * 或返回的向量不合法（为空或维度不符）时自动降级到下一级；全部失败时抛出最后一个异常。
 * 维度不合法（避免污染向量库）与空响应均视为失败。每级自身通过HTTP超时保证不会无限阻塞。
 */
@Slf4j
public class ResilientEmbeddingModel implements EmbeddingModel {

  /** 按优先级排列的各级向量模型。 */
  private final List<EmbeddingModel> delegates;

  /** 期望的向量维度（与入库配置一致，用于校验并拒绝异常响应）。 */
  private final int dimensions;

  /**
   * @param delegates 按优先级排列的向量模型列表
   * @param dimensions 期望的向量维度
   */
  public ResilientEmbeddingModel(List<EmbeddingModel> delegates, int dimensions) {
    this.delegates = delegates == null ? List.of() : List.copyOf(delegates);
    this.dimensions = dimensions;
  }

  /**
   * 依次尝试各级向量服务完成批量向量化请求。
   *
   * @param request 批量向量化请求
   * @return 某一级返回的合法响应
   * @throws RuntimeException 全部级别均失败时抛出最后一次的异常
   */
  @Override
  public EmbeddingResponse call(EmbeddingRequest request) {
    RuntimeException lastFailure = null;
    for (int index = 0; index < delegates.size(); index++) {
      try {
        EmbeddingResponse response = delegates.get(index).call(request);
        if (hasValidEmbeddings(response, request.getInstructions().size())) {
          logFallback(index);
          return response;
        }
        lastFailure = new IllegalStateException("向量模型返回了不合法响应");
        log.warn("第{}级向量模型返回不合法响应，降级到下一级", index + 1);
      } catch (RuntimeException e) {
        lastFailure = e;
        log.warn("第{}级向量模型调用失败，降级到下一级：{}", index + 1, e.getMessage());
      }
    }
    throw lastFailure != null ? lastFailure : new IllegalStateException("未配置任何向量模型");
  }

  /**
   * 依次尝试各级向量服务生成单个文档的向量。
   *
   * @param document 待向量化文档
   * @return 维度合法的向量
   * @throws RuntimeException 全部级别均失败时抛出最后一次的异常
   */
  @Override
  public float[] embed(Document document) {
    RuntimeException lastFailure = null;
    for (int index = 0; index < delegates.size(); index++) {
      try {
        float[] vector = delegates.get(index).embed(document);
        if (isValidVector(vector)) {
          logFallback(index);
          return vector;
        }
        lastFailure = new IllegalStateException("向量维度不符或为空");
        log.warn("第{}级向量模型返回维度不符的向量，降级到下一级", index + 1);
      } catch (RuntimeException e) {
        lastFailure = e;
        log.warn("第{}级向量模型调用失败，降级到下一级：{}", index + 1, e.getMessage());
      }
    }
    throw lastFailure != null ? lastFailure : new IllegalStateException("未配置任何向量模型");
  }

  /**
   * 依次尝试各级向量服务生成单条文本的向量。
   *
   * @param text 待向量化文本
   * @return 维度合法的向量
   * @throws RuntimeException 全部级别均失败时抛出最后一次的异常
   */
  @Override
  public float[] embed(String text) {
    RuntimeException lastFailure = null;
    for (int index = 0; index < delegates.size(); index++) {
      try {
        float[] vector = delegates.get(index).embed(text);
        if (isValidVector(vector)) {
          logFallback(index);
          return vector;
        }
        lastFailure = new IllegalStateException("向量维度不符或为空");
        log.warn("第{}级向量模型返回维度不符的向量，降级到下一级", index + 1);
      } catch (RuntimeException e) {
        lastFailure = e;
        log.warn("第{}级向量模型调用失败，降级到下一级：{}", index + 1, e.getMessage());
      }
    }
    throw lastFailure != null ? lastFailure : new IllegalStateException("未配置任何向量模型");
  }

  /**
   * 依次尝试各级向量服务批量生成文本向量。
   *
   * @param texts 待向量化文本列表
   * @return 与输入顺序对应的合法向量列表
   * @throws RuntimeException 全部级别均失败时抛出最后一次的异常
   */
  @Override
  public List<float[]> embed(List<String> texts) {
    if (texts == null || texts.isEmpty()) {
      return List.of();
    }
    RuntimeException lastFailure = null;
    for (int index = 0; index < delegates.size(); index++) {
      try {
        List<float[]> vectors = delegates.get(index).embed(texts);
        if (hasValidVectors(vectors, texts.size())) {
          logFallback(index);
          return vectors;
        }
        lastFailure = new IllegalStateException("向量数量或维度不符");
        log.warn("第{}级向量模型返回不合法向量列表，降级到下一级", index + 1);
      } catch (RuntimeException e) {
        lastFailure = e;
        log.warn("第{}级向量模型调用失败，降级到下一级：{}", index + 1, e.getMessage());
      }
    }
    throw lastFailure != null ? lastFailure : new IllegalStateException("未配置任何向量模型");
  }

  @Override
  public int dimensions() {
    return dimensions;
  }

  /**
   * 记录降级成功日志（首级成功不记录）。
   *
   * @param index 实际成功的级别下标
   */
  private void logFallback(int index) {
    if (index > 0) {
      log.warn("向量模型已降级至第{}级并完成本次调用", index + 1);
    }
  }

  /**
   * 校验单个向量是否合法。
   *
   * @param vector 向量
   * @return 非空且维度符合期望时返回true
   */
  private boolean isValidVector(float[] vector) {
    return vector != null && vector.length == dimensions;
  }

  /**
   * 校验向量列表是否合法。
   *
   * @param vectors 向量列表
   * @param expectedCount 期望的向量数量
   * @return 数量与每个向量维度均合法时返回true
   */
  private boolean hasValidVectors(List<float[]> vectors, int expectedCount) {
    if (vectors == null || vectors.size() != expectedCount) {
      return false;
    }
    for (float[] vector : vectors) {
      if (!isValidVector(vector)) {
        return false;
      }
    }
    return true;
  }

  /**
   * 校验向量响应是否合法。
   *
   * @param response 向量响应
   * @param expectedCount 期望的向量数量
   * @return 响应结构、数量与每个向量维度均合法时返回true
   */
  private boolean hasValidEmbeddings(EmbeddingResponse response, int expectedCount) {
    if (response == null || response.getResults() == null
        || response.getResults().size() != expectedCount) {
      return false;
    }
    for (Embedding embedding : response.getResults()) {
      if (embedding == null || !isValidVector(embedding.getOutput())) {
        return false;
      }
    }
    return true;
  }
}
