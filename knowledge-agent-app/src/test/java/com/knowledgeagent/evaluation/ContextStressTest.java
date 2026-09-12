package com.knowledgeagent.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgeagent.ai.pojo.dto.ConversationChatRequest;
import com.knowledgeagent.ai.service.ConversationChatService;
import com.knowledgeagent.conversation.mapper.ConversationMapper;
import com.knowledgeagent.conversation.pojo.entity.Conversation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 上下文管理压力测试：单会话连续进行数百轮对话，首轮埋入关键事实（商品/订单号/签收日期/会员身份），
 * 中途两次事实更新（补充订单内商品与赠品、开通PLUS会员），每30轮插入召回探针，
 * 观察多次摘要压缩后关键信息是否丢失。压缩事件通过会话summary文本变化检测。
 * 报告输出 target/evaluation/context_stress_result.json，进度实时写 context_stress_progress.log。
 * 运行方式：RUN_EVAL=true mvn test -pl knowledge-agent-app -Dtest=ContextStressTest
 * 可选环境变量 CONTEXT_STRESS_TURNS 覆盖总轮数（默认300）。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_EVAL", matches = "true")
class ContextStressTest {

  /** 首轮埋入的完整售后场景（商品、订单号、签收日期、会员身份全在此轮）。 */
  private static final String PLANT_TEXT =
      "你好，我要咨询一个售后问题。我在京东自营买了一个戴森HD16吹风机，订单号JD20260904，"
          + "9月1日签收的，用了三天发现开机有异响。先说明一下，我不是PLUS会员。";

  /** 事实更新轮次：补充订单内其他商品与赠品。 */
  private static final int EXTRA_ITEM_TURN = 120;

  /** 事实更新轮次：开通PLUS会员。 */
  private static final int PLUS_UPDATE_TURN = 240;

  /** 探针间隔轮数。 */
  private static final int PROBE_INTERVAL = 30;

  /** 填充轮消息池：知识问题（触发检索）+ 场景补充 + 寒暄，循环使用撑大上下文。 */
  private static final String[] FILLERS = {
    "京东上商品出现质量问题怎么退换？",
    "淘宝七天无理由退货的时限怎么计算？",
    "消费者享有哪些主要权利？",
    "白条支付的订单退款退到哪里？",
    "京东自营商品维修的运费怎么算？",
    "买到假货或者被欺诈可以要求几倍赔偿？",
    "淘宝哪些商品不支持七天无理由退货？",
    "京东退款多久能到账？",
    "网购七日无理由退货哪些商品不适用？",
    "在京东怎么提交售后申请？",
    "顺便说一下，这个吹风机是我给我妈买的，她不太会用手机App，后面如果需要操作什么你一步步告诉我就行。",
    "我平时上班比较忙，只有晚上有空处理这个售后，你们晚上还能办理吗？",
    "我家住在北京朝阳区，附近就有京东的自提点，如果需要我自己送过去也可以。",
    "对了，我之前在淘宝也买过一个电吹风，用了两年都没问题，这次京东这个才三天就异响，有点失望。",
    "我朋友说质量问题可以要求换货，换货的话大概要多久能拿到新的？",
    "如果最终要退货，退款是原路退回吗？我当时用的是微信支付。",
    "好的，我了解了。",
    "谢谢你，我先看看商品。",
    "嗯，你刚才说的我记下了。",
    "稍等，我查一下手机上的订单详情。"
  };

  /** 探针问题池，按P1-P4循环。 */
  private static final String[] PROBES = {
    "先不要查知识库，凭你对我的记忆回答：我最初咨询的是什么商品、出了什么问题？订单号是多少？",
    "凭记忆回答，不要查知识库：我是什么时候签收商品的？我现在是不是PLUS会员？",
    "凭记忆回答：我们的对话里我有没有提到订单里还买了其他商品？提到了的话说具体是什么。",
    "凭记忆回答，不要查知识库：我的售后问题目前处理到什么进度了？"
  };

  /** 多轮会话编排能力。 */
  @Autowired private ConversationChatService conversationChatService;

  /** 会话表访问，用于快照摘要状态。 */
  @Autowired private ConversationMapper conversationMapper;

  /** JSON读写。 */
  @Autowired private ObjectMapper objectMapper;

  /** 总轮数。 */
  private final int totalTurns = resolveTurns();

  /**
   * 执行长对话压力测试：逐轮驱动会话，快照摘要状态，捕获压缩事件与探针结果，最终输出报告。
   */
  @Test
  void longConversationRetention() {
    List<ScriptedTurn> script = buildScript();
    Map<String, Object> report = PathSupport.linkedMap();
    List<Map<String, Object>> turnLogs = PathSupport.list();
    List<Map<String, Object>> compressionLogs = PathSupport.list();
    List<Map<String, Object>> probeLogs = PathSupport.list();
    report.put("totalTurns", totalTurns);
    report.put("compressions", compressionLogs);
    report.put("probes", probeLogs);
    report.put("turns", turnLogs);

    Long conversationId = null;
    String prevSummary = null;
    try {
      for (int i = 0; i < script.size(); i++) {
        ScriptedTurn st = script.get(i);
        int turnNo = i + 1;
        long start = System.currentTimeMillis();
        var response =
            conversationChatService.chat(new ConversationChatRequest(conversationId, st.text()));
        long elapsed = System.currentTimeMillis() - start;
        conversationId = response.getConversationId();

        Conversation conv = conversationMapper.selectById(conversationId);
        String summary = conv == null ? null : conv.getSummary();
        boolean compressed = summary != null && !summary.equals(prevSummary);
        if (compressed) {
          Map<String, Object> c = PathSupport.linkedMap();
          c.put("turnIndex", turnNo);
          c.put("summaryChars", summary.length());
          c.put("summaryText", summary);
          compressionLogs.add(c);
        }
        prevSummary = summary;

        Map<String, Object> t = PathSupport.linkedMap();
        t.put("turnIndex", turnNo);
        t.put("type", st.type());
        t.put("user", st.text());
        t.put("answer", response.getAnswer());
        t.put("elapsedMs", elapsed);
        t.put("summaryChars", summary == null ? 0 : summary.length());
        t.put("compressed", compressed);
        turnLogs.add(t);
        appendProgress(turnNo, st, response.getAnswer(), summary, compressed, elapsed);

        if ("PROBE".equals(st.type())) {
          Map<String, Object> p = PathSupport.linkedMap();
          p.put("turnIndex", turnNo);
          p.put("probeKind", st.probeKind());
          p.put("question", st.text());
          p.put("answer", response.getAnswer());
          p.put("compressedBeforeProbe", compressed);
          p.put("autoCheck", autoCheck(st.probeKind(), turnNo, response.getAnswer()));
          probeLogs.add(p);
        }
        System.out.printf(
            "[turn %d/%d] %s summaryChars=%d compressed=%s elapsed=%dms%n",
            turnNo, script.size(), st.type(), summary == null ? 0 : summary.length(), compressed, elapsed);
      }
    } finally {
      report.put("finalSummary", prevSummary);
      PathSupport.writeReport(objectMapper, "context_stress_result.json", report);
    }
  }

  /**
   * 构建对话脚本：首轮埋事实，固定轮次做事实更新，每30轮探针，其余填充。
   *
   * @return 逐轮脚本
   */
  private List<ScriptedTurn> buildScript() {
    List<ScriptedTurn> script = new ArrayList<>(totalTurns);
    int probeCount = 0;
    int fillerIdx = 0;
    for (int turnNo = 1; turnNo <= totalTurns; turnNo++) {
      if (turnNo == 1) {
        script.add(new ScriptedTurn("PLANT", PLANT_TEXT, null));
      } else if (turnNo == EXTRA_ITEM_TURN) {
        script.add(
            new ScriptedTurn(
                "UPDATE",
                "对了，补充一个信息：那个订单里我还一起买了一个小米电热水壶，赠品是一个保温杯，"
                    + "电水壶没什么问题，就不用管它。",
                null));
      } else if (turnNo == PLUS_UPDATE_TURN) {
        script.add(
            new ScriptedTurn(
                "UPDATE", "跟你说一下，我昨天刚开通了PLUS会员，以后按PLUS会员的规则来。", null));
      } else if (turnNo % PROBE_INTERVAL == 0) {
        int kindIdx = probeCount % PROBES.length;
        probeCount++;
        script.add(new ScriptedTurn("PROBE", PROBES[kindIdx], "P" + (kindIdx + 1)));
      } else {
        script.add(new ScriptedTurn("FILLER", FILLERS[fillerIdx % FILLERS.length], null));
        fillerIdx++;
      }
    }
    return script;
  }

  /**
   * 探针答案的自动关键词检查（辅助判断，最终以人工裁判为准）。
   *
   * @param probeKind 探针类型P1-P4
   * @param turnNo 探针所在轮次
   * @param answer 探针回答
   * @return 检查项到是否命中的映射
   */
  private Map<String, Boolean> autoCheck(String probeKind, int turnNo, String answer) {
    Map<String, Boolean> checks = PathSupport.linkedMap();
    switch (probeKind == null ? "" : probeKind) {
      case "P1" -> {
        checks.put("商品(戴森/吹风机)", find(answer, "戴森|吹风机"));
        checks.put("订单号(JD20260904)", find(answer, "JD20260904|20260904"));
      }
      case "P2" -> {
        checks.put("签收日期(9月1日)", find(answer, "9月1|九月一|09-01|09/01|9\\.1"));
        if (turnNo >= PLUS_UPDATE_TURN) {
          checks.put("PLUS(应为已开通)", find(answer, "已开通|开通了|现在是|已是"));
        } else {
          checks.put("PLUS(应为未开通)", find(answer, "不是|未开通|没开通|没有开通|非PLUS"));
        }
      }
      case "P3" -> {
        boolean mentioned = find(answer, "小米|电水壶|电热水壶|保温杯");
        if (turnNo >= EXTRA_ITEM_TURN) {
          checks.put("补充商品(小米电水壶)", find(answer, "小米|电水壶|电热水壶"));
          checks.put("赠品(保温杯)", find(answer, "保温杯"));
        } else {
          checks.put("未提及(此时不应有)", !mentioned);
        }
      }
      case "P4" -> checks.put("进度作答(非空)", answer != null && !answer.isBlank());
      default -> {}
    }
    return checks;
  }

  /**
   * 判断文本是否命中正则。
   *
   * @param text 待检文本
   * @param regex 正则
   * @return 命中返回true
   */
  private boolean find(String text, String regex) {
    return text != null && Pattern.compile(regex).matcher(text).find();
  }

  /**
   * 追加一行进度到实时日志文件，便于长跑过程中人工监控。
   *
   * @param turnNo 轮次
   * @param st 脚本轮
   * @param answer 本轮回答
   * @param summary 当前摘要
   * @param compressed 本轮是否发生压缩
   * @param elapsed 本轮耗时毫秒
   */
  private void appendProgress(
      int turnNo, ScriptedTurn st, String answer, String summary, boolean compressed, long elapsed) {
    try {
      Path dir = Path.of("target", "evaluation");
      Files.createDirectories(dir);
      String line =
          String.format(
              "[%s] turn %d/%d %s compressed=%s summaryChars=%d elapsed=%dms | Q: %s | A(%d): %s%n",
              java.time.LocalTime.now().withNano(0),
              turnNo,
              totalTurns,
              st.type(),
              compressed,
              summary == null ? 0 : summary.length(),
              elapsed,
              st.text(),
              answer == null ? 0 : answer.length(),
              answer == null ? "" : answer.substring(0, Math.min(120, answer.length())));
      Files.writeString(
          dir.resolve("context_stress_progress.log"),
          line,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (Exception e) {
      // 进度日志失败不影响测试本身
    }
  }

  /**
   * 解析总轮数：环境变量CONTEXT_STRESS_TURNS优先，默认300。
   *
   * @return 总轮数
   */
  private int resolveTurns() {
    String env = System.getenv("CONTEXT_STRESS_TURNS");
    if (env != null && !env.isBlank()) {
      try {
        return Math.max(30, Integer.parseInt(env.strip()));
      } catch (NumberFormatException ignored) {
        // 非法值回退默认
      }
    }
    return 300;
  }

  /** 脚本轮：类型（PLANT/UPDATE/PROBE/FILLER）、文本与探针类型。 */
  private record ScriptedTurn(String type, String text, String probeKind) {}
}
