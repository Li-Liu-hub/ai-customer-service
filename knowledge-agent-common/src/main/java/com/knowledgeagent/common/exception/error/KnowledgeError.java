package com.knowledgeagent.common.exception.error;

import com.knowledgeagent.common.exception.BusinessException;

/** 集中维护知识文件处理相关的业务错误。 */
public enum KnowledgeError {
  FILE_REQUIRED(2001, "请选择需要上传的文件", 400),
  FILE_EMPTY(2002, "上传文件不能为空", 400),
  FILE_TOO_LARGE(2003, "上传文件超过大小限制", 413),
  FILE_NAME_INVALID(2004, "文件名称不合法", 400),
  FILE_TYPE_NOT_SUPPORTED(2005, "不支持该文件类型", 415),
  FILE_CONTENT_TYPE_MISMATCH(2006, "文件内容与扩展名不匹配", 415),
  FILE_READ_FAILED(2007, "读取上传文件失败", 400),
  FILE_PARSE_FAILED(2008, "解析上传文件失败", 422),
  TEXT_EMPTY_AFTER_CLEANING(2009, "文件中没有可入库的文本内容", 422),
  CHUNK_CONFIGURATION_INVALID(2010, "文本切片配置不合法", 500),
  TEXT_CHUNK_FAILED(2011, "文件文本无法生成有效切片", 422),
  FILE_STORAGE_CONFIGURATION_INVALID(2012, "文件存储目录配置不合法", 500),
  FILE_SAVE_FAILED(2013, "保存上传文件失败", 500),
  EMBEDDING_FAILED(2014, "生成文本向量失败", 502),
  EMBEDDING_RESPONSE_INVALID(2015, "向量模型返回的数据不合法", 502),
  KNOWLEDGE_SAVE_FAILED(2016, "保存知识文件和分块失败", 500),
  FILE_DELETE_FAILED(2017, "清理本地文件失败", 500),
  KNOWLEDGE_QUERY_REQUIRED(2018, "请输入需要查询的知识问题", 400),
  KNOWLEDGE_SEARCH_FAILED(2019, "查询知识失败", 500),
  KNOWLEDGE_LIMIT_INVALID(2020, "知识查询返回数量必须在1到20之间", 400),
  KNOWLEDGE_FILE_NOT_FOUND(2021, "指定的知识文件不存在", 404),
  KNOWLEDGE_REPLACE_FAILED(2022, "替换知识文件失败", 500),
  DOCUMENT_TYPE_REQUIRED(2023, "请选择文档业务类型", 400),
  FILE_BASE64_INVALID(2024, "文件Base64内容不合法", 400),
  DAILY_SUMMARY_DATE_INVALID(2025, "每日总结日期格式不合法", 400),
  DAILY_SUMMARY_CONTENT_REQUIRED(2026, "每日总结正文不能为空", 400),
  KB_NAME_REQUIRED(2027, "请指定知识库名称", 400),
  DOCUMENT_TYPE_INVALID(2028, "不支持的文档业务类型", 400),
  KB_FILE_NOT_FOUND(2029, "指定知识库下没有已入库的文件", 404);

  /** 业务错误码。 */
  private final int code;

  /** 返回给调用方的错误信息。 */
  private final String message;

  /** HTTP 状态码。 */
  private final int httpStatus;

  KnowledgeError(int code, String message, int httpStatus) {
    this.code = code;
    this.message = message;
    this.httpStatus = httpStatus;
  }

  /**
   * 根据当前知识错误创建业务异常。
   *
   * @return 包含错误码、错误信息和 HTTP 状态的业务异常
   */
  public BusinessException exception() {
    return new BusinessException(code, message, httpStatus);
  }

  /**
   * 根据当前知识错误和原始异常创建业务异常。
   *
   * @param cause 触发当前知识错误的原始异常
   * @return 保留原始错误原因的业务异常
   */
  public BusinessException exception(Throwable cause) {
    return new BusinessException(code, message, httpStatus, cause);
  }
}
