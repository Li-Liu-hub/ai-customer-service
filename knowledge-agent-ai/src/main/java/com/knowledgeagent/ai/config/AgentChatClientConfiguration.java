package com.knowledgeagent.ai.config;

import com.knowledgeagent.ai.prompt.AgentPrompts;
import com.knowledgeagent.ai.tools.KnowledgeTools;
import com.knowledgeagent.knowledge.service.KnowledgeService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 组装对话Agent：基础问答、带知识检索工具的智能客服Agent、会话摘要压缩与会话标题生成。 */
@Configuration
public class AgentChatClientConfiguration {

  /**
   * 创建最基本对话Agent对应的ChatClient，系统提示词由框架封装为SystemMessage注入。
   *
   * @param chatModel 配置文件启用的聊天模型（本地OpenAI兼容服务）
   * @return 基本对话使用的ChatClient
   */
  @Bean
  public ChatClient basicChatAgent(ChatModel chatModel) {
    return ChatClient.builder(chatModel)
        .defaultSystem(AgentPrompts.BASIC_CHAT_SYSTEM.getText())
        .build();
  }

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
   * 创建负责把旧摘要与待压缩对话轮次压缩为新会话摘要的ChatClient，用于上下文超限时的压缩。
   *
   * @param chatModel 配置文件启用的聊天模型
   * @return 会话摘要压缩使用的ChatClient
   */
  @Bean
  public ChatClient summaryChatClient(ChatModel chatModel) {
    return ChatClient.builder(chatModel)
        .defaultSystem(AgentPrompts.SUMMARY_SYSTEM.getText())
        .build();
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
