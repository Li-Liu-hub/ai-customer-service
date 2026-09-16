package com.knowledgeagent.conversation.controller;

import com.knowledgeagent.common.response.ApiResponse;
import com.knowledgeagent.conversation.pojo.vo.ConversationMessageVO;
import com.knowledgeagent.conversation.pojo.vo.ConversationSummaryVO;
import com.knowledgeagent.conversation.service.ConversationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 提供会话与历史消息查询接口。 */
@Tag(name = "会话查询", description = "会话列表与历史消息查询")
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

  /** 会话查询服务。 */
  private final ConversationQueryService conversationQueryService;

  /**
   * 查询最近的会话列表。
   *
   * @param limit 最大返回数量，默认50
   * @return 会话摘要列表（按更新时间倒序）
   */
  @Operation(summary = "查询会话列表", description = "按最近更新时间倒序返回会话摘要列表")
  @GetMapping
  public ApiResponse<List<ConversationSummaryVO>> listConversations(
      @RequestParam(value = "limit", required = false, defaultValue = "50") int limit) {
    return ApiResponse.success(conversationQueryService.listConversations(limit));
  }

  /**
   * 查询指定会话的历史消息（按时间正序）。
   *
   * @param conversationId 会话ID
   * @return 历史消息列表
   */
  @Operation(summary = "查询会话历史消息", description = "按时间正序返回指定会话的全部消息")
  @GetMapping("/{conversationId}/messages")
  public ApiResponse<List<ConversationMessageVO>> listMessages(
      @PathVariable("conversationId") Long conversationId) {
    return ApiResponse.success(conversationQueryService.listMessages(conversationId));
  }
}
