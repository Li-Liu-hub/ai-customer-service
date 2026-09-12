package com.knowledgeagent.knowledge.pojo.vo;

/** 知识分块检索结果视图：携带来源信息（文件标题、知识库、素材来源）供引用作答。 */
public record KnowledgeChunkVO(
    Long chunkId,
    Long fileId,
    String fileTitle,
    String kbName,
    String source,
    Integer chunkIndex,
    String content,
    Integer tokenCount,
    Double distance) {}
