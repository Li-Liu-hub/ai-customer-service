package com.knowledgeagent.conversation.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

/** 会话实体，对应conversation表。 */
@Data
@TableName("conversation")
public class Conversation {

  /** 会话ID，应用侧雪花算法生成。 */
  @TableId private Long id;

  /** 会话标题。 */
  private String title;

  /** 创建时间。 */
  private OffsetDateTime createTime;

  /** 最近更新时间，压缩摘要后刷新。 */
  private OffsetDateTime updateTime;

  /** 已压缩会话摘要。 */
  private String summary;

  /** 摘要截止水位：id不超过该值的消息已被压缩进摘要。 */
  private Long summarizedUntilId;

  /** 摘要Token数。 */
  private Integer summaryTokens;
}