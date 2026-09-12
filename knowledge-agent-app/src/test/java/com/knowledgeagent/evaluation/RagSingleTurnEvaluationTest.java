package com.knowledgeagent.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgeagent.knowledge.pojo.dto.KnowledgeSearchDTO;
import com.knowledgeagent.knowledge.pojo.vo.KnowledgeChunkVO;
import com.knowledgeagent.knowledge.service.KnowledgeService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 纯RAG单轮评估：对每个测试用例分别度量——
 * 检索层：原文库与问答库各自的 hit@5 与 MRR（客观精确计算）；
 * 端到端：智能客服Agent（带检索工具）的回答关键词覆盖率与LLM裁判评分（本地模型）。
 * 运行方式：RUN_EVAL=true mvn test -pl knowledge-agent-app -Dtest=RagSingleTurnEvaluationTest
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_EVAL", matches = "true")
class RagSingleTurnEvaluationTest {

  /** 检索评估的topK。 */
  private static final int RETRIEVAL_TOP_K = 5;

  /** 域到原文库/问答库的映射。 */
  private static final Map<String, String[]> KB_BY_DOMAIN =
      Map.of(
          "退货规则", new String[] {"退货规则原文库", "退货规则问答库"},
          "用户权益", new String[] {"用户权益原文库", "用户权益问答库"},
          "平台操作指南", new String[] {"平台操作原文库", "平台操作问答库"},
          "平台操作", new String[] {"平台操作原文库", "平台操作问答库"});

  /** 知识库能力。 */
  @Autowired private KnowledgeService knowledgeService;

  /** 智能客服Agent（带知识检索工具）。 */
  @Autowired @Qualifier("agentChatClient") private ChatClient agentChatClient;

  /** 本地对话模型，用作LLM裁判。 */
  @Autowired private ChatModel chatModel;

  /** JSON读写。 */
  @Autowired private ObjectMapper objectMapper;

  /**
   * 执行全部单轮测试集评估并输出报告。
   */
  @Test
  void evaluate() throws Exception {
    String[] setFiles = {
      "evaluation/rag_single_return_policy.json",
      "evaluation/rag_single_user_rights.json",
      "evaluation/rag_single_platform_guide.json",
      "evaluation/rag_single_hard_return.json",
      "evaluation/rag_single_hard_rights.json",
      "evaluation/rag_single_hard_platform.json"
    };
    Map<String, Object> report = new LinkedHashMap<>();
    for (String setFile : setFiles) {
      EvalModels.SingleTurnSet set =
          objectMapper.readValue(
              getClass().getClassLoader().getResourceAsStream(setFile),
              EvalModels.SingleTurnSet.class);
      report.put(set.setId(), evaluateSet(set));
    }
    PathSupport.writeReport(objectMapper, "single_turn_result.json", report);
  }

  /**
   * 评估单个测试集：逐用例跑双库检索对比与端到端问答。
   *
   * @param set 测试集
   * @return 该集的评估结果（含各指标汇总）
   */
  private Map<String, Object> evaluateSet(EvalModels.SingleTurnSet set) {
    String[] kbs = KB_BY_DOMAIN.get(set.domain());
    if (kbs == null) {
      throw new IllegalStateException("未知域：" + set.domain());
    }
    List<Map<String, Object>> caseResults = new ArrayList<>();
    int rawHits = 0;
    int qaHits = 0;
    double rawMrrSum = 0;
    double qaMrrSum = 0;
    int keywordPass = 0;
    int forbiddenPass = 0;
    double judgeSum = 0;
    int judgeCount = 0;

    for (EvalModels.SingleCase c : set.cases()) {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("caseId", c.caseId());
      result.put("question", c.question());

      int[] raw = retrievalMetrics(c, kbs[0]);
      int[] qa = retrievalMetrics(c, kbs[1]);
      rawHits += raw[0];
      qaHits += qa[0];
      rawMrrSum += 1.0 / raw[1];
      qaMrrSum += 1.0 / qa[1];
      result.put("rawHit", raw[0] == 1);
      result.put("rawRank", raw[1]);
      result.put("qaHit", qa[0] == 1);
      result.put("qaRank", qa[1]);

      Map<String, Object> agentResult = runAgent(c);
      result.putAll(agentResult);
      if ((Boolean) agentResult.get("keywordPass")) {
        keywordPass++;
      }
      if ((Boolean) agentResult.get("forbiddenPass")) {
        forbiddenPass++;
      }
      double judge = (Double) agentResult.get("judgeScore");
      if (judge >= 0) {
        judgeSum += judge;
        judgeCount++;
      }

      System.out.printf(
          "[%s] %s raw(h=%d,r=%d) qa(h=%d,r=%d) kw=%s judge=%.1f%n",
          c.caseId(),
          c.question().substring(0, Math.min(20, c.question().length())),
          raw[0],
          raw[1],
          qa[0],
          qa[1],
          agentResult.get("keywordPass"),
          judge);
      caseResults.add(result);
    }

    int total = set.cases().size();
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("caseCount", total);
    summary.put("rawHitAt5", (double) rawHits / total);
    summary.put("qaHitAt5", (double) qaHits / total);
    summary.put("rawMrr", rawMrrSum / total);
    summary.put("qaMrr", qaMrrSum / total);
    summary.put("keywordCoverage", (double) keywordPass / total);
    summary.put("forbiddenClean", (double) forbiddenPass / total);
    summary.put("judgeScoreAvg", judgeCount == 0 ? -1 : judgeSum / judgeCount);

    System.out.printf(
        "=== %s（%d条）rawHit@5=%.1f%% qaHit@5=%.1f%% rawMRR=%.3f qaMRR=%.3f 关键词=%.1f%% 裁判=%.1f ===%n",
        set.setId(),
        total,
        100.0 * rawHits / total,
        100.0 * qaHits / total,
        rawMrrSum / total,
        qaMrrSum / total,
        100.0 * keywordPass / total,
        judgeCount == 0 ? -1 : judgeSum / judgeCount);

    Map<String, Object> setResult = new LinkedHashMap<>();
    setResult.put("summary", summary);
    setResult.put("cases", caseResults);
    return setResult;
  }

  /**
   * 计算单用例在指定知识库的检索指标。
   *
   * @param c 测试用例
   * @param kbName 知识库名称
   * @return [是否命中(0/1), 首个命中块排名（未命中为大数）]
   */
  private int[] retrievalMetrics(EvalModels.SingleCase c, String kbName) {
    List<KnowledgeChunkVO> hits =
        knowledgeService.searchKnowledge(new KnowledgeSearchDTO(c.question(), kbName, RETRIEVAL_TOP_K));
    int rank = RETRIEVAL_TOP_K + 1;
    for (int i = 0; i < hits.size(); i++) {
      if (matchesAll(hits.get(i).content(), c.retrievalMustContain())) {
        rank = i + 1;
        break;
      }
    }
    boolean hit = rank <= RETRIEVAL_TOP_K;
    return new int[] {hit ? 1 : 0, hit ? rank : RETRIEVAL_TOP_K + 1};
  }

  /**
   * 跑端到端智能客服问答并计算关键词覆盖、禁词与裁判评分。
   *
   * @param c 测试用例
   * @return 端到端评估结果
   */
  private Map<String, Object> runAgent(EvalModels.SingleCase c) {
    Map<String, Object> result = new LinkedHashMap<>();
    String answer;
    try {
      answer = agentChatClient.prompt().user(c.question()).call().content();
    } catch (Exception e) {
      result.put("answer", "AGENT_ERROR: " + e.getMessage());
      result.put("keywordPass", false);
      result.put("forbiddenPass", false);
      result.put("judgeScore", -1.0);
      return result;
    }
    if (answer == null || answer.isBlank()) {
      answer = "";
    }
    result.put("answer", answer);
    result.put("keywordPass", matchesAll(answer, c.answerMustContain()));
    boolean forbiddenClean = c.answerMustNotContain().stream().noneMatch(answer::contains);
    result.put("forbiddenPass", forbiddenClean);
    result.put("judgeScore", judge(c.question(), c.groundTruth(), answer));
    return result;
  }

  /**
   * LLM裁判：本地模型按参考答案给回答打0-10分，解析失败返回-1。
   *
   * @param question 问题
   * @param groundTruth 参考答案
   * @param answer 待评回答
   * @return 0-10分；失败为-1
   */
  private double judge(String question, String groundTruth, String answer) {
    try {
      String prompt =
          "你是答案质量评估员。对比参考答案评估AI回答的准确性和完整性。"
              + "【问题】" + question
              + "【参考答案】" + groundTruth
              + "【AI回答】" + answer
              + "请打分（0-10分，10分最好）。只输出一个数字。";
      String response = chatModel.call(prompt);
      if (response == null) {
        return -1;
      }
      java.util.regex.Matcher m = Pattern.compile("\\d+(\\.\\d+)?").matcher(response);
      return m.find() ? Double.parseDouble(m.group()) : -1;
    } catch (Exception e) {
      return -1;
    }
  }

  /**
   * 判断文本是否匹配全部正则关键词。
   *
   * @param text 待检文本
   * @param patterns 正则列表（元素间AND）
   * @return 全部命中返回true
   */
  private boolean matchesAll(String text, List<String> patterns) {
    if (patterns == null || patterns.isEmpty()) {
      return true;
    }
    for (String p : patterns) {
      if (!Pattern.compile(p).matcher(text).find()) {
        return false;
      }
    }
    return true;
  }
}
