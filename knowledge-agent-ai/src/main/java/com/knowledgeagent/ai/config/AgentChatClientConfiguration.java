package com.knowledgeagent.ai.config;

import com.knowledgeagent.ai.prompt.AgentPrompts;
import com.knowledgeagent.ai.tools.KnowledgeTools;
import com.knowledgeagent.knowledge.service.KnowledgeService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 组装对话Agent：带知识检索工具的智能客服Agent、会话摘要压缩与会话标题生成。 */
@Configuration
public class AgentChatClientConfiguration {

  /**
   * 创建智能客服Agent：挂载知识检索工具（枚举知识库+语义检索），
   * 模型在回答业务问题前自主决定是否调用工具检索知识库。
   *
   * @param chatModel 配置文件启用的聊天模型
   * @param knowledgeService 知识库能力（来自knowledge模块）
   * @return 智能客服Agent使用的ChatClient
   */
  @Bean
  public ChatClient agentChatClient(ChatModel chatModel, KnowledgeService knowledgeService) {
    return ChatClient.builder(chatModel)
        .defaultSystem(AgentPrompts.AGENT_SYSTEM.getText())
        .defaultTools(new KnowledgeTools(knowledgeService))
        .build();
  }

  /**
   * 创建会话摘要Agent：绑定摘要压缩系统提示词，把旧摘要与待压缩轮次合并为新的交接摘要。
   *
   * @param chatModel 配置文件启用的聊天模型
   * @return 会话摘要Agent
   */
  @Bean
  public SummaryAgent summaryAgent(ChatModel chatModel) {
    ChatClient summaryChatClient =
        ChatClient.builder(chatModel)
            .defaultSystem(AgentPrompts.SUMMARY_SYSTEM.getText())
            .build();
    return new SummaryAgent(summaryChatClient);
  }

  /**
   * 创建负责根据用户首轮消息生成会话标题的ChatClient。
   *
   * @param chatModel 配置文件启用的聊天模型
   * @return 会话标题生成使用的ChatClient
   */
  @Bean
  public ChatClient titleChatClient(ChatModel chatModel) {
    return ChatClient.builder(chatModel)
        .defaultSystem(AgentPrompts.TITLE_SYSTEM.getText())
        .build();
  }
}
