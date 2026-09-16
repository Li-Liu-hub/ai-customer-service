package com.knowledgeagent.ai.controller;

import com.knowledgeagent.ai.pojo.dto.ConversationChatRequest;
import com.knowledgeagent.ai.pojo.dto.ConversationChatResponse;
import com.knowledgeagent.ai.service.ConversationChatService;
import com.knowledgeagent.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供多轮对话接口：首次对话创建会话，后续携带会话ID续接上下文。 */
@Tag(name = "多轮对话", description = "会话创建与续聊")
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationChatController {

  /** 多轮对话编排服务。 */
  private final ConversationChatService conversationChatService;

  /**
   * 处理一次多轮对话。
   *
   * @param request 会话ID与当前用户消息；会话ID为空表示新对话
   * @return 会话ID与AI回答
   */
  @Operation(
      summary = "发起多轮对话",
      description = "首次对话不传会话ID（自动创建会话），后续携带会话ID续接上下文")
  @PostMapping("/chat")
  public ApiResponse<ConversationChatResponse> chat(
      @Valid @RequestBody ConversationChatRequest request) {
    return ApiResponse.success(conversationChatService.chat(request));
  }
}
