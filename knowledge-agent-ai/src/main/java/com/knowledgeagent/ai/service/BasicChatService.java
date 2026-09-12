package com.knowledgeagent.ai.service;

import com.knowledgeagent.ai.pojo.dto.BasicChatRequest;

/** 最基本对话的服务接口。 */
public interface BasicChatService {

  /**
   * 基于本地模型对用户消息进行多轮问答。
   *
   * @param request 用户消息及可选的对话历史
   * @return 模型生成的回答文本
   */
  String chat(BasicChatRequest request);
}