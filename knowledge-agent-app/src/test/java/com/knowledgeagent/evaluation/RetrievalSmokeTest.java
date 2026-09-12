package com.knowledgeagent.evaluation;

import com.knowledgeagent.knowledge.pojo.dto.KnowledgeSearchDTO;
import com.knowledgeagent.knowledge.pojo.vo.KnowledgeChunkVO;
import com.knowledgeagent.knowledge.service.KnowledgeService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 检索链路冒烟测试：不跑完整评估，仅验证「向量检索→RRF混合→专用模型重排」全链路可用，
 * 用于管线配置变更（重写/混合/重排开关、模型切换）后的快速自检。
 * 运行方式：RUN_EVAL=true mvn test -pl knowledge-agent-app -Dtest=RetrievalSmokeTest
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_EVAL", matches = "true")
class RetrievalSmokeTest {

  /** 知识库检索能力。 */
  @Autowired private KnowledgeService knowledgeService;

  /**
   * 在退货规则原文库执行一次带重排的检索并输出top命中，验证检索链路端到端可用。
   */
  @Test
  void searchWithRerank() {
    List<KnowledgeChunkVO> hits =
        knowledgeService.searchKnowledge(
            new KnowledgeSearchDTO("个人原因退货的运费谁承担", "退货规则原文库", 5));
    org.junit.jupiter.api.Assertions.assertFalse(hits.isEmpty(), "检索应返回非空结果");
    System.out.println("=== 检索top命中（经重排） ===");
    for (int i = 0; i < hits.size(); i++) {
      String content = hits.get(i).content();
      System.out.printf(
          "[%d] chunkId=%d %s%n",
          i + 1,
          hits.get(i).chunkId(),
          content.substring(0, Math.min(80, content.length())).replace('\n', ' '));
    }
  }
}
