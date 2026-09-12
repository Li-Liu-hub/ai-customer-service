package com.knowledgeagent.ai.pojo.vo;

import java.time.OffsetDateTime;

/**
 * 会话历史消息视图：单轮对话的用户消息与AI回复。
 *
 * @param id 消息ID
 * @param userMessage 用户消息内容
 * @param aiMessage AI回复内容（处理中时为空）
 * @param createTime 创建时间
 */
public record ConversationMessageVO(
    Long id, String userMessage, String aiMessage, OffsetDateTime createTime) {}
