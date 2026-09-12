package com.knowledgeagent.knowledge.pojo.vo;

import java.time.OffsetDateTime;

/** 知识文件视图：文件列表与入库结果返回给调用方时使用。 */
public record KnowledgeFileVO(
    Long fileId,
    String kbName,
    String title,
    String documentType,
    String source,
    String fileFormat,
    Long fileSize,
    Integer chunkCount,
    String embeddingModel,
    OffsetDateTime createTime) {}
