package com.knowledgeagent.knowledge.pojo.vo;

/** 知识库聚合视图：按kb_name聚合的文件数与分块数，供Agent工具枚举知识库。 */
public record KnowledgeBaseVO(String kbName, Long fileCount, Long chunkCount) {}
