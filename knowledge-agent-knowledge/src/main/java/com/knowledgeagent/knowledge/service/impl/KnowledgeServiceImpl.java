package com.knowledgeagent.knowledge.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgeagent.common.config.EmbeddingProperties;
import com.knowledgeagent.common.config.FileStorageProperties;
import com.knowledgeagent.common.config.FileValidationProperties;
import com.knowledgeagent.common.config.TextChunkProperties;
import com.knowledgeagent.common.exception.error.KnowledgeError;
import com.knowledgeagent.common.util.FileParserUtil;
import com.knowledgeagent.common.util.FileStorageUtil;
import com.knowledgeagent.common.util.FileValidationUtil;
import com.knowledgeagent.common.util.FormatUtil;
import com.knowledgeagent.common.util.TextCleaningUtil;
import com.knowledgeagent.common.util.TokenEstimateUtil;
import com.knowledgeagent.knowledge.config.RagProperties;
import com.knowledgeagent.knowledge.mapper.KnowledgeChunkMapper;
import com.knowledgeagent.knowledge.mapper.KnowledgeFileMapper;
import com.knowledgeagent.knowledge.pojo.dto.KnowledgeSearchDTO;
import com.knowledgeagent.knowledge.pojo.entity.KnowledgeChunk;
import com.knowledgeagent.knowledge.pojo.entity.KnowledgeFile;
import com.knowledgeagent.knowledge.pojo.enums.DocumentType;
import com.knowledgeagent.knowledge.pojo.vo.KnowledgeBaseVO;
import com.knowledgeagent.knowledge.pojo.vo.KnowledgeChunkVO;
import com.knowledgeagent.knowledge.pojo.vo.KnowledgeFileVO;
import com.knowledgeagent.knowledge.service.KnowledgeService;
import com.knowledgeagent.knowledge.util.TextChunkUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库能力实现：入库链路（校验→保存→解析→清洗→切块→向量化→落库）
 * 与检索链路（重写→向量检索→RRF混合→专用重排模型重排），各调优环节由配置开关控制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

  /** RRF融合常数，rank从1开始，score=Σ 1/(K+rank)。 */
  private static final int RRF_K = 60;

  /** 知识文件表访问。 */
  private final KnowledgeFileMapper fileMapper;

  /** 知识切片表访问。 */
  private final KnowledgeChunkMapper chunkMapper;

  /** 向量模型（BGE-M3），入库与检索共用同一模型保证向量空间一致。 */
  private final EmbeddingModel embeddingModel;

  /** 对话模型（本地spark2.5），问题重写使用，开关关闭时不调用。 */
  private final ChatModel chatModel;

  /** 向量模型配置。 */
  private final EmbeddingProperties embeddingProperties;

  /** 切块长度配置。 */
  private final TextChunkProperties textChunkProperties;

  /** 本地文件存储配置。 */
  private final FileStorageProperties fileStorageProperties;

  /** 文件校验配置。 */
  private final FileValidationProperties fileValidationProperties;

  /** RAG检索配置（各调优开关）。 */
  private final RagProperties ragProperties;

  /** JSON序列化（重排服务请求构建与响应解析）。 */
  private final ObjectMapper objectMapper;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public KnowledgeFileVO ingestFile(
      MultipartFile file, String kbName, String documentType, String source) {
    if (kbName == null || kbName.isBlank()) {
      throw KnowledgeError.KB_NAME_REQUIRED.exception();
    }
    DocumentType type = parseDocumentType(documentType);
    FileValidationUtil.validateKnowledgeFile(file, fileValidationProperties);

    Long fileId = IdWorker.getId();
    String fileLocation =
        FileStorageUtil.saveKnowledgeFile(file, fileId, fileStorageProperties);
    try {
      String rawText = FileParserUtil.parse(file);
      String cleanText = TextCleaningUtil.clean(rawText);
      List<String> chunkTexts = TextChunkUtil.chunk(cleanText, textChunkProperties, type);
      List<float[]> vectors = embedAll(chunkTexts);
      List<KnowledgeChunk> chunks = buildChunks(fileId, chunkTexts, vectors);

      KnowledgeFile entity = new KnowledgeFile();
      entity.setId(fileId);
      entity.setKbName(kbName.strip());
      entity.setTitle(file.getOriginalFilename());
      entity.setDocumentType(type.name());
      entity.setSource(source);
      entity.setFileFormat(FormatUtil.fileExtension(file.getOriginalFilename()));
      entity.setFileSize(file.getSize());
      entity.setFileLocation(fileLocation);
      entity.setChunkCount(chunks.size());
      entity.setEmbeddingModel(embeddingProperties.modelName());
      fileMapper.insert(entity);
      if (!chunks.isEmpty()) {
        chunkMapper.batchInsert(chunks);
      }
      return toFileVO(entity);
    } catch (RuntimeException e) {
      // 入库链路任意环节失败：清理已保存的本地文件后原样抛出，数据库由事务回滚
      FileStorageUtil.deleteKnowledgeFile(fileLocation, fileStorageProperties);
      throw e;
    }
  }

  @Override
  public List<KnowledgeBaseVO> listKnowledgeBases() {
    return fileMapper.aggregateBases();
  }

  @Override
  public List<KnowledgeFileVO> listFiles(String kbName) {
    return fileMapper.selectFiles(kbName);
  }

  @Override
  public List<KnowledgeChunkVO> searchKnowledge(KnowledgeSearchDTO dto) {
    if (dto == null || dto.query() == null || dto.query().isBlank()) {
      throw KnowledgeError.KNOWLEDGE_QUERY_REQUIRED.exception();
    }
    String query = dto.query().strip();
    int topK = resolveTopK(dto.topK());
    String kbName = dto.kbName() == null || dto.kbName().isBlank() ? null : dto.kbName().strip();

    String effectiveQuery =
        ragProperties.queryRewriteEnabled() ? rewriteQuery(query) : query;

    // 检索阶段取回数量：重排开启时需要更多候选
    int fetchLimit =
        ragProperties.rerankEnabled()
            ? Math.max(topK, positiveOrDefault(ragProperties.rerankCandidates(), topK))
            : topK;
    List<KnowledgeChunkVO> hits = vectorSearch(effectiveQuery, kbName, fetchLimit);

    if (ragProperties.hybridSearchEnabled()) {
      hits = fuseWithKeyword(effectiveQuery, kbName, hits, fetchLimit);
    }
    if (ragProperties.rerankEnabled() && hits.size() > topK) {
      hits = rerank(effectiveQuery, hits);
    }
    return hits.size() > topK ? new ArrayList<>(hits.subList(0, topK)) : hits;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void deleteFile(Long fileId) {
    KnowledgeFile file = fileMapper.selectById(fileId);
    if (file == null) {
      throw KnowledgeError.KNOWLEDGE_FILE_NOT_FOUND.exception();
    }
    chunkMapper.deleteByFileId(fileId);
    fileMapper.deleteById(fileId);
    FileStorageUtil.deleteKnowledgeFile(file.getFileLocation(), fileStorageProperties);
  }

  /**
   * 解析并校验文档业务类型。
   *
   * @param documentType 类型名称字符串
   * @return 文档业务类型枚举
   * @throws com.knowledgeagent.common.exception.BusinessException 类型为空或不支持时抛出
   */
  private DocumentType parseDocumentType(String documentType) {
    if (documentType == null || documentType.isBlank()) {
      throw KnowledgeError.DOCUMENT_TYPE_REQUIRED.exception();
    }
    try {
      return DocumentType.valueOf(documentType.strip());
    } catch (IllegalArgumentException e) {
      throw KnowledgeError.DOCUMENT_TYPE_INVALID.exception(e);
    }
  }

  /**
   * 分批调用向量模型生成全部分块的向量。
   *
   * @param texts 分块文本列表
   * @return 与文本顺序对应的向量列表
   * @throws com.knowledgeagent.common.exception.BusinessException 向量调用失败或维度不符时抛出
   */
  private List<float[]> embedAll(List<String> texts) {
    int batchSize = Math.max(1, embeddingProperties.batchSize());
    List<float[]> vectors = new ArrayList<>(texts.size());
    try {
      for (int i = 0; i < texts.size(); i += batchSize) {
        List<String> batch = texts.subList(i, Math.min(i + batchSize, texts.size()));
        List<float[]> embedded = embeddingModel.embed(batch);
        for (float[] vector : embedded) {
          if (vector == null || vector.length != embeddingProperties.dimensions()) {
            throw KnowledgeError.EMBEDDING_RESPONSE_INVALID.exception();
          }
          vectors.add(vector);
        }
      }
    } catch (RuntimeException e) {
      throw KnowledgeError.EMBEDDING_FAILED.exception(e);
    }
    return vectors;
  }

  /**
   * 组装切片实体列表：ID、序号与Token数。
   *
   * @param fileId 所属文件ID
   * @param texts 分块文本
   * @param vectors 分块向量
   * @return 切片实体列表
   */
  private List<KnowledgeChunk> buildChunks(
      Long fileId, List<String> texts, List<float[]> vectors) {
    List<KnowledgeChunk> chunks = new ArrayList<>(texts.size());
    for (int i = 0; i < texts.size(); i++) {
      KnowledgeChunk chunk = new KnowledgeChunk();
      chunk.setId(IdWorker.getId());
      chunk.setFileId(fileId);
      chunk.setChunkIndex(i);
      chunk.setContent(texts.get(i));
      chunk.setEmbedding(FormatUtil.toVectorLiteral(vectors.get(i)));
      chunk.setTextLength(texts.get(i).length());
      chunk.setTokenCount(TokenEstimateUtil.countTokens(texts.get(i)));
      chunks.add(chunk);
    }
    return chunks;
  }

  /**
   * 向量相似检索：查询向量化后调用HNSW检索，知识库过滤时放大候选量。
   *
   * @param query 生效查询文本
   * @param kbName 知识库名称；为null时检索全部
   * @param fetchLimit 取回数量
   * @return 命中分块列表
   */
  private List<KnowledgeChunkVO> vectorSearch(String query, String kbName, int fetchLimit) {
    float[] queryVector = embedQuery(query);
    String literal = FormatUtil.toVectorLiteral(queryVector);
    int oversample =
        kbName == null ? 1 : Math.max(1, ragProperties.oversampleFactor());
    return chunkMapper.selectSimilar(
        literal, kbName, fetchLimit * oversample, fetchLimit);
  }

  /**
   * 关键词通道RRF融合：向量结果与2-gram关键词命中结果按倒数排名融合。
   *
   * @param query 生效查询文本
   * @param kbName 知识库名称；为null时不过滤
   * @param vectorHits 向量检索结果
   * @param fetchLimit 关键词通道取回数量
   * @return 融合排序后的分块列表
   */
  private List<KnowledgeChunkVO> fuseWithKeyword(
      String query, String kbName, List<KnowledgeChunkVO> vectorHits, int fetchLimit) {
    List<String> grams = FormatUtil.extractKeywordGrams(query);
    if (grams.isEmpty()) {
      return vectorHits;
    }
    String gramsLiteral = FormatUtil.toPgTextArrayLiteral(grams);
    List<Long> keywordIds = chunkMapper.selectByKeywordGrams(gramsLiteral, kbName, fetchLimit);
    if (keywordIds.isEmpty()) {
      return vectorHits;
    }

    Map<Long, KnowledgeChunkVO> byId = new LinkedHashMap<>();
    for (KnowledgeChunkVO hit : vectorHits) {
      byId.put(hit.chunkId(), hit);
    }
    // 关键词命中但向量未召回的分块：取回详情补入
    List<Long> missing = new ArrayList<>();
    for (Long id : keywordIds) {
      if (!byId.containsKey(id)) {
        missing.add(id);
      }
    }
    if (!missing.isEmpty()) {
      byId.putAll(indexById(chunkMapper.selectByIds(missing)));
    }

    Map<Long, Double> scores = new HashMap<>();
    for (int i = 0; i < vectorHits.size(); i++) {
      scores.merge(vectorHits.get(i).chunkId(), 1.0 / (RRF_K + i + 1), Double::sum);
    }
    for (int i = 0; i < keywordIds.size(); i++) {
      scores.merge(keywordIds.get(i), 1.0 / (RRF_K + i + 1), Double::sum);
    }
    List<KnowledgeChunkVO> fused = new ArrayList<>(byId.values());
    fused.sort((a, b) -> Double.compare(scores.getOrDefault(b.chunkId(), 0.0),
        scores.getOrDefault(a.chunkId(), 0.0)));
    return fused;
  }

  /**
   * 调用对话模型做问题重写：把口语化问题改写为适合向量检索的完整问句，失败时降级原问题。
   *
   * @param query 原始查询
   * @return 重写后的查询；失败时返回原查询
   */
  private String rewriteQuery(String query) {
    try {
      String prompt =
          "你是客服系统的检索查询改写器，把口语化的用户问题改写成适合知识库检索的书面问句。\n"
              + "规则：\n"
              + "1. 保留原问题的全部关键信息：平台名、商品、数字（天数、金额、倍数）；\n"
              + "2. 只把口语说法换成书面说法，不添加原问题没有的信息，不回答问题；\n"
              + "3. 只输出改写后的问题，不要解释。\n"
              + "示例：\n"
              + "用户问题：在京东买了个东西最多几天内能退啊\n"
              + "改写：京东购买的商品几天内可以退货\n"
              + "用户问题：那要是不要了的话邮费谁出\n"
              + "改写：退货时邮费由谁承担\n"
              + "用户问题：淘宝上买的活的那种吃的能不能无理由退\n"
              + "改写：淘宝鲜活易腐类商品是否支持七天无理由退货\n"
              + "用户问题："
              + query
              + "\n改写：";
      String rewritten = chatModel.call(prompt);
      if (rewritten == null || rewritten.isBlank()) {
        return query;
      }
      return rewritten.strip();
    } catch (Exception e) {
      log.warn("问题重写失败，降级使用原查询：{}", e.getMessage());
      return query;
    }
  }

  /**
   * 构建重排服务HTTP请求工厂：带连接与读取超时，避免重排服务异常时拖垮检索链路。
   *
   * @return 带超时配置的请求工厂
   */
  private SimpleClientHttpRequestFactory rerankRequestFactory() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(5));
    factory.setReadTimeout(ragProperties.rerankTimeout());
    return factory;
  }

  /**
   * 调用专用重排服务（bge-reranker，OpenAI兼容 /v1/rerank）对候选分块
   * 按查询相关性重排，失败时降级保持原序。
   *
   * @param query 生效查询文本
   * @param candidates 候选分块（数量大于topK）
   * @return 重排后的分块列表（与候选等长，最相关在前）
   */
  private List<KnowledgeChunkVO> rerank(String query, List<KnowledgeChunkVO> candidates) {
    try {
      Map<String, Object> request = new LinkedHashMap<>();
      request.put("model", ragProperties.rerankModelName());
      request.put("query", query);
      request.put("documents", candidates.stream().map(KnowledgeChunkVO::content).toList());
      request.put("top_n", candidates.size());
      String raw =
          RestClient.builder()
              .requestFactory(rerankRequestFactory())
              .build()
              .post()
              .uri(ragProperties.rerankBaseUrl() + "/v1/rerank")
              .contentType(MediaType.APPLICATION_JSON)
              .body(request)
              .retrieve()
              .body(String.class);
      List<KnowledgeChunkVO> reranked = new ArrayList<>(candidates.size());
      for (JsonNode item : objectMapper.readTree(raw).path("results")) {
        int index = item.path("index").asInt(-1);
        if (index >= 0 && index < candidates.size() && !reranked.contains(candidates.get(index))) {
          reranked.add(candidates.get(index));
        }
      }
      // 服务端未返回全部候选时，剩余候选按原序补在末尾
      for (KnowledgeChunkVO candidate : candidates) {
        if (!reranked.contains(candidate)) {
          reranked.add(candidate);
        }
      }
      return reranked;
    } catch (Exception e) {
      log.warn("结果重排失败，降级保持原序：{}", e.getMessage());
      return candidates;
    }
  }

  /**
   * 生成查询向量。
   *
   * @param query 查询文本
   * @return 查询向量
   * @throws com.knowledgeagent.common.exception.BusinessException 向量化失败或维度不符时抛出
   */
  private float[] embedQuery(String query) {
    float[] vector;
    try {
      vector = embeddingModel.embed(query);
    } catch (RuntimeException e) {
      throw KnowledgeError.EMBEDDING_FAILED.exception(e);
    }
    if (vector == null || vector.length != embeddingProperties.dimensions()) {
      throw KnowledgeError.EMBEDDING_RESPONSE_INVALID.exception();
    }
    return vector;
  }

  /**
   * 解析生效topK：入参为空或越界时使用配置默认值并夹紧到上下限。
   *
   * @param requested 请求指定的topK，可为null
   * @return 生效topK
   */
  private int resolveTopK(Integer requested) {
    int fallback = positiveOrDefault(ragProperties.defaultTopK(), 5);
    int topK = requested == null ? fallback : requested;
    int max = positiveOrDefault(ragProperties.maxTopK(), 20);
    if (topK < 1 || topK > max) {
      throw KnowledgeError.KNOWLEDGE_LIMIT_INVALID.exception();
    }
    return topK;
  }

  /**
   * 取正值，非正时返回默认值。
   *
   * @param value 原值
   * @param fallback 默认值
   * @return 正值或默认值
   */
  private int positiveOrDefault(int value, int fallback) {
    return value > 0 ? value : fallback;
  }

  /**
   * 把分块列表按chunkId建立索引。
   *
   * @param hits 分块列表
   * @return chunkId到分块的映射
   */
  private Map<Long, KnowledgeChunkVO> indexById(List<KnowledgeChunkVO> hits) {
    Map<Long, KnowledgeChunkVO> map = new LinkedHashMap<>();
    for (KnowledgeChunkVO hit : hits) {
      map.put(hit.chunkId(), hit);
    }
    return map;
  }

  /**
   * 实体转文件视图。
   *
   * @param entity 知识文件实体
   * @return 文件视图
   */
  private KnowledgeFileVO toFileVO(KnowledgeFile entity) {
    return new KnowledgeFileVO(
        entity.getId(),
        entity.getKbName(),
        entity.getTitle(),
        entity.getDocumentType(),
        entity.getSource(),
        entity.getFileFormat(),
        entity.getFileSize(),
        entity.getChunkCount(),
        entity.getEmbeddingModel(),
        entity.getCreateTime());
  }
}
