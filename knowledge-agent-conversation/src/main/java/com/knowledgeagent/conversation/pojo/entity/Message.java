package com.knowledgeagent.conversation.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

/** 会话内单轮消息实体，对应message表；一轮同时存用户消息与AI回复。 */
@Data
@TableName("message")
public class Message {

  /** 消息ID，应用侧雪花算法生成。 */
  @TableId private Long id;

  /** 所属会话ID。 */
  private Long conversationId;

  /** 用户消息内容。 */
  private String userMessage;

  /** AI回复内容，尚未回复时为空。 */
  private String aiMessage;

  /** 创建时间。 */
  private OffsetDateTime createTime;

  /** 用户消息Token数。 */
  private Integer userTokens;

  /** AI回复Token数。 */
  private Integer aiTokens;
}