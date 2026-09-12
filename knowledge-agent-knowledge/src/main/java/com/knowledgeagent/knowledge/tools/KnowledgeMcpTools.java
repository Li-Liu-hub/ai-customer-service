package com.knowledgeagent.knowledge.tools;

import com.knowledgeagent.knowledge.pojo.dto.KnowledgeSearchDTO;
import com.knowledgeagent.knowledge.pojo.vo.KnowledgeBaseVO;
import com.knowledgeagent.knowledge.pojo.vo.KnowledgeChunkVO;
import com.knowledgeagent.knowledge.service.KnowledgeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * 通过 /mcp 对外暴露的知识库服务端工具。
 * 服务端工具必须使用 @McpTool（springaicommunity 注解库）：Spring AI 服务端注解扫描器
 * 只识别 @McpTool/@McpResource/@McpPrompt/@McpComplete，不识别客户端工具注解 @Tool。
 */
@Component
@RequiredArgsConstructor
public class KnowledgeMcpTools {

  /** 知识库能力。 */
  private final KnowledgeService knowledgeService;

  /**
   * 枚举系统中的全部知识库，返回每个知识库的名称、文件数与分块数。
   *
   * @return 知识库聚合列表
   */
  @McpTool(
      name = "query_knowledge_bases",
      description = "查询系统中有哪些知识库。返回每个知识库的名称、文件数和分块数。")
  public List<KnowledgeBaseVO> queryKnowledgeBases() {
    return knowledgeService.listKnowledgeBases();
  }

  /**
   * 在指定知识库中做语义检索，返回与问题最相关的知识分块（含来源文件标题）。
   *
   * @param query 要检索的问题文本
   * @param kbName 目标知识库名称；不确定时传空字符串检索全部知识库
   * @param topK 期望返回的分块数量，建议3到5
   * @return 命中分块列表（内容与来源）
   */
  @McpTool(
      name = "search_knowledge",
      description =
          "在知识库中语义检索与问题相关的知识分块。kbName为目标知识库名称（来自query_knowledge_bases的结果），"
              + "传空字符串则搜索全部知识库；topK为返回数量，建议3到5。")
  public List<KnowledgeChunkVO> searchKnowledge(
      @McpToolParam(description = "要检索的问题文本") String query,
      @McpToolParam(description = "目标知识库名称，空字符串表示全部", required = false) String kbName,
      @McpToolParam(description = "返回分块数量，3到5", required = false) Integer topK) {
    String targetKb = kbName == null || kbName.isBlank() ? null : kbName;
    Integer limit = topK == null || topK < 1 || topK > 20 ? null : topK;
    return knowledgeService.searchKnowledge(new KnowledgeSearchDTO(query, targetKb, limit));
  }
}
