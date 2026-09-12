package com.knowledgeagent.systemlog.pojo;

/** 定义系统日志能够记录的业务操作类型。 */
public enum OperationType {
  UPLOAD_FILE,
  REPLACE_FILE,
  QUERY_FILES,
  QUERY_KNOWLEDGE_CHUNKS,
  CREATE_CONVERSATION,
  CONTINUE_CONVERSATION,
  QUERY_CONVERSATIONS,
  QUERY_MESSAGES,
  QUERY_LOGS,
  GET_CURRENT_TIME
}
