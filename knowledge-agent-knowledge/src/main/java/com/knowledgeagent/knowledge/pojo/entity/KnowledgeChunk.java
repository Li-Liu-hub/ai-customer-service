package com.knowledgeagent.knowledge.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 知识切片实体，对应knowledge_chunk表。
 * embedding以PostgreSQL向量字面量字符串（如"[0.1,0.2]"）承载，SQL侧CAST为vector类型；
 * metadata以JSON字符串承载，SQL侧CAST为jsonb类型。
 */
@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk {

  /** 分块ID，应用侧雪花算法生成。 */
  @TableId private Long id;

  /** 所属文件ID。 */
  private Long fileId;

  /** 文件内分块序号，从0开始。 */
  private Integer chunkIndex;

  /** 分块原始文本。 */
  private String content;

  /** 文本向量字面量（写入时CAST为vector(1024)）。 */
  private String embedding;

  /** 文本字符长度。 */
  private Integer textLength;

  /** 文本Token数量。 */
  private Integer tokenCount;

  /** 标签元数据JSON字符串（写入时CAST为jsonb）。 */
  private String metadata;

  /** 创建时间。 */
  private java.time.OffsetDateTime createTime;
}
