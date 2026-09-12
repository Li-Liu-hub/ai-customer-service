package com.knowledgeagent.evaluation;

import java.util.List;

/** 评估测试集的JSON模型定义。 */
public final class EvalModels {

  private EvalModels() {}

  /** 纯RAG单轮测试集。 */
  public record SingleTurnSet(String setId, String domain, String description, List<SingleCase> cases) {}

  /** 纯RAG单轮测试用例。 */
  public record SingleCase(
      String caseId,
      String question,
      String groundTruth,
      List<String> retrievalMustContain,
      List<String> answerMustContain,
      List<String> answerMustNotContain) {}

  /** 多轮对话测试集。 */
  public record MultiTurnSet(String setId, String domain, String description, List<MultiCase> cases) {}

  /** 多轮对话测试用例。 */
  public record MultiCase(String caseId, String scenario, List<String> turns, List<Checkpoint> checkpoints) {}

  /** 多轮检查点：atTurnIndex为0基轮次下标，mustContain为正则（元素间AND）。 */
  public record Checkpoint(int atTurnIndex, String type, String assertion, List<String> mustContain) {}
}
