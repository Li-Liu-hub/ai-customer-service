package com.knowledgeagent.knowledge.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

/** 知识文件实体，对应knowledge_file表：一次入库的文档元信息。 */
@Data
@TableName("knowledge_file")
public class KnowledgeFile {

  /** 文件ID，应用侧雪花算法生成。 */
  @TableId private Long id;

  /** 所属知识库名称（自描述命名，Agent工具按该列聚合列出知识库）。 */
  private String kbName;

  /** 文件标题。 */
  private String title;

  /** 文档业务类型（RAW_DOC / QA_SET，应用层枚举管控）。 */
  private String documentType;

  /** 素材来源标识（jd / taobao / gov / constructed 等），供检索结果溯源。 */
  private String source;

  /** 文件格式（扩展名小写）。 */
  private String fileFormat;

  /** 文件大小（字节）。 */
  private Long fileSize;

  /** 原始文件在本地存储中的相对路径。 */
  private String fileLocation;

  /** 切块数量。 */
  private Integer chunkCount;

  /** 入库时使用的向量模型名称。 */
  private String embeddingModel;

  /** 创建时间。 */
  private OffsetDateTime createTime;

  /** 更新时间。 */
  private OffsetDateTime updateTime;
}
