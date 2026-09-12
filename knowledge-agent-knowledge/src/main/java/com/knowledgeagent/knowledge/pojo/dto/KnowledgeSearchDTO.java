package com.knowledgeagent.knowledge.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 知识语义检索请求参数。 */
public record KnowledgeSearchDTO(

    /** 检索问题文本。 */
    @NotBlank(message = "请输入需要查询的知识问题") @Size(max = 1000, message = "查询文本过长")
    String query,

    /** 目标知识库名称；为空时检索全部知识库。 */
    @Size(max = 64, message = "知识库名称过长")
    String kbName,

    /** 期望返回的分块数量；为空时使用配置默认值。 */
    Integer topK) {}
