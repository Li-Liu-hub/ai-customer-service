package com.knowledgeagent.ai.tools;

import com.knowledgeagent.knowledge.pojo.dto.KnowledgeSearchDTO;
import com.knowledgeagent.knowledge.pojo.vo.KnowledgeBaseVO;
import com.knowledgeagent.knowledge.pojo.vo.KnowledgeChunkVO;
import com.knowledgeagent.knowledge.service.KnowledgeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 知识检索Agent工具：把RAG能力以工具形式暴露给对话模型。
 * 模型先枚举知识库了解可检索范围，再带着知识库定位做语义检索。
 */
@Slf4j
@RequiredArgsConstructor
public class KnowledgeTools {

  /** 知识库能力（来自knowledge模块，与REST接口共享同一Service）。 */
  private final KnowledgeService knowledgeService;

  /**
   * 枚举系统中的全部知识库，返回每个知识库的名称、文件数与分块数。
   * 回答涉及平台规则、用户权益或操作流程的问题前，先调用本工具确认可用的知识库。
   *
   * @return 知识库聚合列表
   */
  @Tool(
      description =
          "查询系统中有哪些知识库。返回每个知识库的名称、文件数和分块数。"
              + "在回答用户关于退货规则、用户权益、平台操作指南等问题之前，先调用本工具了解可检索的知识库。")
  public List<KnowledgeBaseVO> queryKnowledgeBases() {
    List<KnowledgeBaseVO> bases = knowledgeService.listKnowledgeBases();
    log.info("工具调用 queryKnowledgeBases：知识库{}个", bases.size());
    return bases;
  }

  /**
   * 在指定知识库中做语义检索，返回与问题最相关的知识分块（含来源文件标题）。
   *
   * @param query 要检索的问题文本
   * @param kbName 目标知识库名称；不确定时传空字符串检索全部知识库
   * @param topK 期望返回的分块数量，建议3到5
   * @return 命中分块列表（内容与来源）
   */
  @Tool(
      description =
          "在知识库中语义检索与问题相关的知识分块。kbName为目标知识库名称（来自queryKnowledgeBases的结果），"
              + "传空字符串则搜索全部知识库；topK为返回数量，建议3到5。")
  public List<KnowledgeChunkVO> searchKnowledge(
      @ToolParam(description = "要检索的问题文本") String query,
      @ToolParam(description = "目标知识库名称，空字符串表示全部", required = false) String kbName,
      @ToolParam(description = "返回分块数量，3到5", required = false) Integer topK) {
    String targetKb = kbName == null || kbName.isBlank() ? null : kbName;
    Integer limit = topK == null || topK < 1 || topK > 20 ? null : topK;
    log.info("工具调用 searchKnowledge：query={}, kbName={}, topK={}", query, targetKb, limit);
    return knowledgeService.searchKnowledge(new KnowledgeSearchDTO(query, targetKb, limit));
  }
}
