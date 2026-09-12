package com.knowledgeagent.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgeagent.ai.pojo.dto.ConversationChatRequest;
import com.knowledgeagent.ai.service.ConversationChatService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 多轮对话评估：逐轮驱动智能客服会话，对每个checkpoint做客观关键词判定（正则AND）
 * 与LLM裁判判定（本地模型按断言评yes/no），汇总checkpoint通过率。
 * 运行方式：RUN_EVAL=true mvn test -pl knowledge-agent-app -Dtest=MultiTurnEvaluationTest
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_EVAL", matches = "true")
class MultiTurnEvaluationTest {

  /** 多轮会话编排能力。 */
  @Autowired private ConversationChatService conversationChatService;

  /** 本地对话模型，用作checkpoint裁判。 */
  @Autowired private ChatModel chatModel;

  /** JSON读写。 */
  @Autowired private ObjectMapper objectMapper;

  /**
   * 执行全部多轮测试集评估并输出报告。
   */
  @Test
  void evaluate() throws Exception {
    String[] setFiles = {
      "evaluation/multi_return_flow.json",
      "evaluation/multi_rights_dispute.json",
      "evaluation/multi_platform_operation.json",
      "evaluation/multi_hard_mix.json"
    };
    Map<String, Object> report = PathSupport.linkedMap();
    for (String setFile : setFiles) {
      EvalModels.MultiTurnSet set =
          objectMapper.readValue(
              getClass().getClassLoader().getResourceAsStream(setFile),
              EvalModels.MultiTurnSet.class);
      report.put(set.setId(), evaluateSet(set));
    }
    PathSupport.writeReport(objectMapper, "multi_turn_result.json", report);
  }

  /**
   * 评估单个多轮测试集。
   *
   * @param set 测试集
   * @return 评估结果（含checkpoint汇总）
   */
  private Map<String, Object> evaluateSet(EvalModels.MultiTurnSet set) {
    List<Map<String, Object>> caseResults = PathSupport.list();
    int totalCheckpoints = 0;
    int keywordPassCount = 0;
    int judgePassCount = 0;

    for (EvalModels.MultiCase c : set.cases()) {
      List<String> answers = runConversation(c.turns());
      Map<String, Object> caseResult = PathSupport.linkedMap();
      caseResult.put("caseId", c.caseId());
      caseResult.put("scenario", c.scenario());

      List<Map<String, Object>> checkpointResults = PathSupport.list();
      for (EvalModels.Checkpoint cp : c.checkpoints()) {
        totalCheckpoints++;
        String targetAnswer = answers.get(cp.atTurnIndex());
        boolean keywordPass = PathSupport.matchesAll(targetAnswer, cp.mustContain());
        boolean judgePass = judgeCheckpoint(c, answers, cp);
        if (keywordPass) {
          keywordPassCount++;
        }
        if (judgePass) {
          judgePassCount++;
        }
        Map<String, Object> cpResult = PathSupport.linkedMap();
        cpResult.put("type", cp.type());
        cpResult.put("atTurnIndex", cp.atTurnIndex());
        cpResult.put("keywordPass", keywordPass);
        cpResult.put("judgePass", judgePass);
        cpResult.put("answer", targetAnswer);
        checkpointResults.add(cpResult);
        System.out.printf("  [%s] T%d %s kw=%s judge=%s%n",
            c.caseId(), cp.atTurnIndex(), cp.type(), keywordPass, judgePass);
      }
      caseResult.put("checkpoints", checkpointResults);
      caseResult.put("turns", c.turns());
      caseResult.put("answers", answers);
      caseResults.add(caseResult);
    }

    Map<String, Object> summary = PathSupport.linkedMap();
    summary.put("caseCount", set.cases().size());
    summary.put("checkpointCount", totalCheckpoints);
    summary.put("keywordPassRate", totalCheckpoints == 0 ? 0 : (double) keywordPassCount / totalCheckpoints);
    summary.put("judgePassRate", totalCheckpoints == 0 ? 0 : (double) judgePassCount / totalCheckpoints);
    System.out.printf("=== %s checkpoint=%d 关键词通过率=%.1f%% 裁判通过率=%.1f%% ===%n",
        set.setId(), totalCheckpoints,
        100.0 * keywordPassCount / Math.max(1, totalCheckpoints),
        100.0 * judgePassCount / Math.max(1, totalCheckpoints));

    Map<String, Object> setResult = PathSupport.linkedMap();
    setResult.put("summary", summary);
    setResult.put("cases", caseResults);
    return setResult;
  }

  /**
   * 驱动一轮完整多轮会话，收集每轮AI回答。
   *
   * @param turns 用户消息序列
   * @return 每轮的AI回答列表（与turns等长）
   */
  private List<String> runConversation(List<String> turns) {
    List<String> answers = PathSupport.list();
    Long conversationId = null;
    for (String turn : turns) {
      var response = conversationChatService.chat(new ConversationChatRequest(conversationId, turn));
      conversationId = response.getConversationId();
      answers.add(response.getAnswer());
    }
    return answers;
  }

  /**
   * LLM裁判判定checkpoint：把完整对话与断言交给本地模型判断是否满足。
   *
   * @param c 测试用例
   * @param answers 各轮AI回答
   * @param cp 检查点
   * @return 模型判定通过返回true；解析失败按false计
   */
  private boolean judgeCheckpoint(EvalModels.MultiCase c, List<String> answers, EvalModels.Checkpoint cp) {
    try {
      StringBuilder dialogue = new StringBuilder();
      for (int i = 0; i <= cp.atTurnIndex() && i < c.turns().size(); i++) {
        dialogue.append("用户：").append(c.turns().get(i)).append('\n');
        dialogue.append("客服：").append(answers.get(i)).append('\n');
      }
      String prompt =
          "你是客服质量评估员。以下是多轮客服对话记录和一条评估要求。"
              + "判断对话是否满足该要求。"
              + "【对话记录】" + dialogue
              + "【评估要求】" + cp.assertion()
              + "如果满足输出yes，不满足输出no。只输出yes或no。";
      String response = chatModel.call(prompt);
      return response != null && response.strip().toLowerCase().startsWith("yes");
    } catch (Exception e) {
      return false;
    }
  }
}
