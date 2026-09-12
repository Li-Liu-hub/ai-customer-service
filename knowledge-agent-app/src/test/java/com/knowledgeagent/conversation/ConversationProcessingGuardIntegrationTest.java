package com.knowledgeagent.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knowledgeagent.conversation.pojo.entity.Conversation;
import com.knowledgeagent.conversation.pojo.entity.Message;
import com.knowledgeagent.conversation.service.ConversationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 会话并发保护与摘要水位线的数据库集成测试：验证处理权抢占/释放、消息按ID水位线回放、
 * 水位线只进不退。需要本地 PostgreSQL（docker compose 容器）运行。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_TEST", matches = "true")
class ConversationProcessingGuardIntegrationTest {

  /** 会话存储能力。 */
  @Autowired private ConversationService conversationService;

  /** 同一会话不允许并发两轮：处理中拒绝，完成后放行。 */
  @Test
  void processingLockAllowsOnlyOneRoundAtATime() {
    Conversation conversation = conversationService.createConversation();

    assertTrue(conversationService.tryAcquireProcessing(conversation.getId()), "首轮应抢占成功");
    assertFalse(
        conversationService.tryAcquireProcessing(conversation.getId()), "处理中应拒绝第二轮");

    conversationService.completeProcessing(conversation.getId());
    assertTrue(
        conversationService.tryAcquireProcessing(conversation.getId()), "完成一轮后应可再次抢占");
    conversationService.completeProcessing(conversation.getId());
  }

  /** 最近会话列表包含新建会话且按更新时间倒序。 */
  @Test
  void recentConversationsIncludeNewestFirst() {
    Conversation conversation = conversationService.createConversation();

    List<Conversation> recent = conversationService.listRecentConversations(50);

    assertFalse(recent.isEmpty());
    assertEquals(conversation.getId(), recent.get(0).getId(), "最新创建的会话应排在首位");
  }

  /** 消息Token在写入时落库（用户消息估算值与AI回复值）。 */
  @Test
  void messageTokensArePersistedOnWrite() {
    Conversation conversation = conversationService.createConversation();
    Message userMessage = conversationService.saveUserMessage(conversation.getId(), "用户消息", 3);
    conversationService.completeAssistantMessage(userMessage.getId(), "AI回复", 7);

    List<Message> persisted = conversationService.getMessagesAfter(conversation.getId(), null);

    assertEquals(1, persisted.size());
    assertEquals(3, persisted.get(0).getUserTokens().intValue(), "用户消息Token应落库");
    assertEquals("AI回复", persisted.get(0).getAiMessage());
    assertEquals(7, persisted.get(0).getAiTokens().intValue(), "AI回复Token应落库");
  }

  /** 消息按ID水位线回放，水位线写入后只进不退。 */
  @Test
  void messagesAreReplayedByWatermarkAndWatermarkOnlyAdvances() {
    Conversation conversation = conversationService.createConversation();
    Message first = conversationService.saveUserMessage(conversation.getId(), "第一条", 1);
    Message second = conversationService.saveUserMessage(conversation.getId(), "第二条", 1);

    List<Message> all = conversationService.getMessagesAfter(conversation.getId(), null);
    assertEquals(2, all.size(), "无水位线时回放全部消息");

    List<Message> afterFirst =
        conversationService.getMessagesAfter(conversation.getId(), first.getId());
    assertEquals(1, afterFirst.size());
    assertEquals(second.getId(), afterFirst.get(0).getId());

    conversationService.applySummary(conversation.getId(), "摘要", second.getId(), 10);
    Conversation reloaded = conversationService.getConversation(conversation.getId());
    assertEquals(second.getId(), reloaded.getSummarizedUntilId());
    assertEquals(10, reloaded.getSummaryTokens().intValue());

    conversationService.applySummary(conversation.getId(), "摘要2", first.getId(), 8);
    Conversation again = conversationService.getConversation(conversation.getId());
    assertEquals(second.getId(), again.getSummarizedUntilId(), "水位线不允许退回");

    List<Message> none =
        conversationService.getMessagesAfter(
            conversation.getId(), again.getSummarizedUntilId());
    assertTrue(none.isEmpty());
  }
}
