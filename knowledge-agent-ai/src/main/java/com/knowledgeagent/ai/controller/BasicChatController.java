package com.knowledgeagent.ai.controller;

import com.knowledgeagent.ai.pojo.dto.BasicChatRequest;
import com.knowledgeagent.ai.service.BasicChatService;
import com.knowledgeagent.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供基于本地模型的最基本多轮对话接口。 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class BasicChatController {

  /** 最基本对话服务。 */
  private final BasicChatService basicChatService;

  /**
   * 接收用户消息及可选历史，返回本地模型的回答。
   *
   * @param request 用户消息和对话历史
   * @return 模型生成的回答文本
   */
  @PostMapping("/basic")
  public ApiResponse<String> chat(@Valid @RequestBody BasicChatRequest request) {
    return ApiResponse.success(basicChatService.chat(request));
  }
}