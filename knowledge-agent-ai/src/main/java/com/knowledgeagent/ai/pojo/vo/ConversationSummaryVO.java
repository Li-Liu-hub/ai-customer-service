package com.knowledgeagent.ai.pojo.vo;

import java.time.OffsetDateTime;

/**
 * 会话列表项视图：会话概要信息，供会话列表接口返回。
 *
 * @param id 会话ID
 * @param title 会话标题（首轮生成，可能为空）
 * @param createTime 创建时间
 * @param updateTime 最近更新时间
 */
public record ConversationSummaryVO(
    Long id, String title, OffsetDateTime createTime, OffsetDateTime updateTime) {}
