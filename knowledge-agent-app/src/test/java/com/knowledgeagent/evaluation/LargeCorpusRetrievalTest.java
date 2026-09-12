package com.knowledgeagent.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgeagent.knowledge.pojo.dto.KnowledgeSearchDTO;
import com.knowledgeagent.knowledge.pojo.vo.KnowledgeChunkVO;
import com.knowledgeagent.knowledge.service.KnowledgeService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 大规模语料纯检索评估：对DuReader-retrieval抽样查询逐条检索目标知识库，
 * 以段落标记[P序号]对照qrels判定命中，计算Recall@5/@10与MRR@5/@10。
 * 不调用任何大语言模型做答案生成；是否经过问题重写/混合/重排由分支的application.yml开关决定。
 * 运行方式：RUN_EVAL=true mvn test -pl knowledge-agent-app -Dtest=LargeCorpusRetrievalTest
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_EVAL", matches = "true")
class LargeCorpusRetrievalTest {

  /** 检索取回数量（同时评估@5与@10）。 */
  private static final int TOP_K = 10;

  /** 段落标记解析模式：形如[P123]。 */
  private static final Pattern MARKER = Pattern.compile("\\[P(\\d+)\\]");

  /** 知识库能力。 */
  @Autowired private KnowledgeService knowledgeService;

  /** JSON读写。 */
  @Autowired private ObjectMapper objectMapper;

  /** 语料配置：知识库名、文档类型、语料目录、评估报告标签。 */
  record CorpusConfig(String kbName, String documentType, String corpusDir, String label) {}

  /** 评估查询：查询ID与文本。 */
  record QueryItem(String qid, String text) {}

  /**
   * 执行全部查询的纯检索评估并输出报告。
   */
  @Test
  void evaluate() throws Exception {
    CorpusConfig config =
        objectMapper.readValue(
            getClass().getClassLoader().getResourceAsStream("evaluation/large_corpus/config.json"),
            CorpusConfig.class);
    List<QueryItem> queries =
        objectMapper.readValue(
            getClass().getClassLoader().getResourceAsStream("evaluation/large_corpus/queries.json"),
            new TypeReference<List<QueryItem>>() {});
    Map<String, List<Integer>> qrels =
        objectMapper.readValue(
            getClass().getClassLoader().getResourceAsStream("evaluation/large_corpus/qrels.json"),
            new TypeReference<Map<String, List<Integer>>>() {});

    List<Map<String, Object>> caseResults = new ArrayList<>();
    int hit5 = 0;
    int hit10 = 0;
    double mrr5Sum = 0;
    double mrr10Sum = 0;
    long start = System.currentTimeMillis();

    for (int i = 0; i < queries.size(); i++) {
      QueryItem q = queries.get(i);
      Set<Integer> relevant = new HashSet<>(qrels.getOrDefault(q.qid(), List.of()));
      List<KnowledgeChunkVO> hits =
          knowledgeService.searchKnowledge(new KnowledgeSearchDTO(q.text(), config.kbName(), TOP_K));

      int firstRank = -1;
      List<Integer> hitPids = new ArrayList<>();
      for (int r = 0; r < hits.size(); r++) {
        Matcher m = MARKER.matcher(hits.get(r).content());
        boolean chunkHit = false;
        while (m.find()) {
          int pid = Integer.parseInt(m.group(1));
          if (relevant.contains(pid)) {
            chunkHit = true;
            hitPids.add(pid);
          }
        }
        if (chunkHit && firstRank < 0) {
          firstRank = r + 1;
        }
      }
      boolean h5 = firstRank > 0 && firstRank <= 5;
      boolean h10 = firstRank > 0;
      if (h5) {
        hit5++;
      }
      if (h10) {
        hit10++;
      }
      mrr5Sum += h5 ? 1.0 / firstRank : 0;
      mrr10Sum += h10 ? 1.0 / firstRank : 0;

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("qid", q.qid());
      result.put("text", q.text());
      result.put("relevantCount", relevant.size());
      result.put("firstHitRank", firstRank);
      result.put("hitPids", hitPids);
      result.put("topMarkers", extractTopMarkers(hits));
      caseResults.add(result);

      if ((i + 1) % 25 == 0) {
        System.out.printf(
            "[%s] 进度 %d/%d Recall@5=%.1f%% MRR@5=%.3f 用时%ds%n",
            config.label(),
            i + 1,
            queries.size(),
            100.0 * hit5 / (i + 1),
            mrr5Sum / (i + 1),
            (System.currentTimeMillis() - start) / 1000);
      }
    }

    int total = queries.size();
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("label", config.label());
    summary.put("kbName", config.kbName());
    summary.put("documentType", config.documentType());
    summary.put("queryCount", total);
    summary.put("recallAt5", (double) hit5 / total);
    summary.put("recallAt10", (double) hit10 / total);
    summary.put("mrrAt5", mrr5Sum / total);
    summary.put("mrrAt10", mrr10Sum / total);
    summary.put("elapsedSeconds", (System.currentTimeMillis() - start) / 1000);

    Map<String, Object> report = new LinkedHashMap<>();
    report.put("summary", summary);
    report.put("cases", caseResults);
    PathSupport.writeReport(
        objectMapper, "large_corpus_" + config.label() + ".json", report);

    System.out.printf(
        "=== %s（%d查询）Recall@5=%.1f%% Recall@10=%.1f%% MRR@5=%.3f MRR@10=%.3f 用时%ds ===%n",
        config.label(),
        total,
        100.0 * hit5 / total,
        100.0 * hit10 / total,
        mrr5Sum / total,
        mrr10Sum / total,
        (System.currentTimeMillis() - start) / 1000);
  }

  /**
   * 提取前若干命中块的段落标记，用于报告人工核查。
   *
   * @param hits 检索命中块
   * @return 每块包含的标记列表
   */
  private List<List<Integer>> extractTopMarkers(List<KnowledgeChunkVO> hits) {
    List<List<Integer>> top = new ArrayList<>();
    for (KnowledgeChunkVO hit : hits) {
      List<Integer> markers = new ArrayList<>();
      Matcher m = MARKER.matcher(hit.content());
      while (m.find()) {
        markers.add(Integer.parseInt(m.group(1)));
      }
      top.add(markers);
    }
    return top;
  }
}
