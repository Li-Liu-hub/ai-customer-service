package com.knowledgeagent.systemlog.pojo.entity;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 系统日志表实体，用于追踪一次系统操作及其模型消耗和错误信息。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemLog {

  /** 日志ID。 */
  private Long id;

  /** 贯穿一次请求的请求ID。 */
  private String requestId;

  /** 操作类型。 */
  private String operationType;

  /** 操作的具体说明。 */
  private String operationDetail;

  /** 本次操作消耗的输入Token数量。 */
  private Integer promptTokens;

  /** 本次操作消耗的输出Token数量。 */
  private Integer completionTokens;

  /** 本次操作使用的模型名称。 */
  private String modelName;

  /** 操作最终状态。 */
  private String status;

  /** 操作失败时的错误码。 */
  private String errorCode;

  /** 操作失败时捕获的错误原因。 */
  private String errorReason;

  /** 本次操作总耗时，单位为毫秒。 */
  private Long latencyMs;

  /** 日志创建时间，也是操作发生时间。 */
  private OffsetDateTime createTime;
}
