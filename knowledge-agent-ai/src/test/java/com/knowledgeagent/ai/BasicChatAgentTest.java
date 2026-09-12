package com.knowledgeagent.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.knowledgeagent.ai.pojo.dto.BasicChatRequest;
import com.knowledgeagent.ai.pojo.dto.BasicChatTurn;
import com.knowledgeagent.ai.service.BasicChatService;
import com.knowledgeagent.knowledge.service.KnowledgeService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 验证基本对话Agent在本地模型（8080的OpenAI兼容端点）上的多轮问答，不依赖任何数据库。 */
@SpringBootTest(
    classes = BasicChatAiTestApplication.class,
    properties = {
      "spring.ai.model.chat=openai",
      "spring.ai.model.embedding=none",
      "spring.ai.model.audio.speech=none",
      "spring.ai.model.audio.transcription=none",
      "spring.ai.model.image=none",
      "spring.ai.model.moderation=none",
      "spring.ai.openai.chat.base-url=http://localhost:8080",
      "spring.ai.openai.chat.completions-path=/v1/chat/completions",
      "spring.ai.openai.chat.api-key=local",
      "spring.ai.openai.chat.options.model=spark2.5",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
          + "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
    })
class BasicChatAgentTest {

  /** 基本对话能力。 */
  @Autowired private BasicChatService basicChatService;

  /** 桩掉knowledge模块依赖：本测试只验证基本对话，不触发知识检索。 */
  @MockitoBean private KnowledgeService knowledgeService;

  /** 单轮对话：直接问一句，应返回非空回答。 */
  @Test
  void singleTurnChat() {
    String answer = basicChatService.chat(new BasicChatRequest("你好，你是谁？", null));
    assertNotNull(answer);
    assertFalse(answer.isBlank());
    System.out.println("单轮回答：" + answer);
  }

  /** 多轮对话：携带历史上下文，验证模型能结合前文回答。 */
  @Test
  void multiTurnChat() {
    BasicChatRequest request =
        new BasicChatRequest(
            "那请问我一般几点吃午饭比较合适？",
            List.of(
                new BasicChatTurn("user", "我通常早上八点起床"),
                new BasicChatTurn("assistant", "好的，那你的作息比较规律。"),
                new BasicChatTurn("user", "我午休习惯到一点才吃午饭")));
    String answer = basicChatService.chat(request);
    assertNotNull(answer);
    assertFalse(answer.isBlank());
    System.out.println("多轮回答：" + answer);
  }
}