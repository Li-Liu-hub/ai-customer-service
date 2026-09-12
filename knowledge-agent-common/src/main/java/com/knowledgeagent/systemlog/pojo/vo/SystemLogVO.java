package com.knowledgeagent.systemlog.pojo.vo;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 返回一条完整的系统操作日志。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemLogVO {

  /** 日志ID。 */
  private Long id;

  /** 贯穿一次请求的请求ID。 */
  private String requestId;

  /** 操作类型。 */
  private String operationType;

  /** JSON格式的操作详情和受影响ID。 */
  private String operationDetail;

  /** 本次操作总耗时毫秒数。 */
  private Long latencyMs;

  /** 输入Token数量。 */
  private Integer promptTokens;

  /** 输出Token数量。 */
  private Integer completionTokens;

  /** 使用的模型名称。 */
  private String modelName;

  /** 操作状态。 */
  private String status;

  /** 操作失败时的错误码。 */
  private String errorCode;

  /** 操作失败时的错误原因。 */
  private String errorReason;

  /** 日志创建时间。 */
  private OffsetDateTime createTime;
}
