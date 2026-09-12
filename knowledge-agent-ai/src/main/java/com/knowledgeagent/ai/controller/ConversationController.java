package com.knowledgeagent.ai.controller;

import com.knowledgeagent.ai.pojo.dto.ConversationChatRequest;
import com.knowledgeagent.ai.pojo.dto.ConversationChatResponse;
import com.knowledgeagent.ai.pojo.vo.ConversationMessageVO;
import com.knowledgeagent.ai.pojo.vo.ConversationSummaryVO;
import com.knowledgeagent.ai.service.ConversationChatService;
import com.knowledgeagent.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 提供多轮对话与会话查询接口：首次对话创建会话，后续携带会话ID续接上下文。 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

  /** 多轮对话编排服务。 */
  private final ConversationChatService conversationChatService;

  /**
   * 处理一次多轮对话。
   *
   * @param request 会话ID与当前用户消息；会话ID为空表示新对话
   * @return 会话ID与AI回答
   */
  @PostMapping("/chat")
  public ApiResponse<ConversationChatResponse> chat(
      @Valid @RequestBody ConversationChatRequest request) {
    return ApiResponse.success(conversationChatService.chat(request));
  }

  /**
   * 查询最近的会话列表。
   *
   * @param limit 最大返回数量，默认50
   * @return 会话摘要列表（按更新时间倒序）
   */
  @GetMapping
  public ApiResponse<List<ConversationSummaryVO>> listConversations(
      @RequestParam(value = "limit", required = false, defaultValue = "50") int limit) {
    return ApiResponse.success(conversationChatService.listConversations(limit));
  }

  /**
   * 查询指定会话的历史消息（按时间正序）。
   *
   * @param conversationId 会话ID
   * @return 历史消息列表
   */
  @GetMapping("/{conversationId}/messages")
  public ApiResponse<List<ConversationMessageVO>> listMessages(
      @PathVariable("conversationId") Long conversationId) {
    return ApiResponse.success(conversationChatService.listMessages(conversationId));
  }
}