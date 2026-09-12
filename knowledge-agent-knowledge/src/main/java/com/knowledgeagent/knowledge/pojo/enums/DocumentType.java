package com.knowledgeagent.knowledge.pojo.enums;

/** 知识文档的业务格式类型，由应用层管控（数据库不设CHECK约束，新增类型无需改表）。 */
public enum DocumentType {
  /** 原始长文档：连续行文的政策/法规/指南原文，入库时按结构切块并合并相邻块。 */
  RAW_DOC("原始长文档"),

  /** 问答集：一问一答结构（## Q 标题分条），入库时一个问题块切一个分块，不跨块合并。 */
  QA_SET("问答集");

  /** 面向调用方的类型说明。 */
  private final String description;

  DocumentType(String description) {
    this.description = description;
  }

  /**
   * 获取类型说明文案。
   *
   * @return 类型说明
   */
  public String getDescription() {
    return description;
  }
}
