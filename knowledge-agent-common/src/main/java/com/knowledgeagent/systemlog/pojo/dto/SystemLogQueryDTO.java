package com.knowledgeagent.systemlog.pojo.dto;

import com.knowledgeagent.systemlog.pojo.OperationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 接收系统日志条件查询参数。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemLogQueryDTO {

  /** 日志ID。 */
  private Long id;

  /** 请求ID。 */
  private String requestId;

  /** 操作类型。 */
  private OperationType operationType;

  /** 操作状态，例如SUCCESS或FAILED。 */
  private String status;

  /** 最大返回数量。 */
  @Min(value = 1, message = "日志返回数量不能小于1")
  @Max(value = 200, message = "日志返回数量不能大于200")
  @Builder.Default
  private Integer limit = 50;
}
