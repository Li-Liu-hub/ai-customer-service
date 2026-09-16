package com.knowledgeagent.systemlog.controller;

import com.knowledgeagent.common.response.ApiResponse;
import com.knowledgeagent.systemlog.pojo.dto.SystemLogQueryDTO;
import com.knowledgeagent.systemlog.pojo.vo.SystemLogVO;
import com.knowledgeagent.systemlog.service.SystemLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供系统日志条件查询接口。 */
@Tag(name = "系统日志", description = "操作日志与Agent审计查询")
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class SystemLogController {

  /** 查询整个系统产生的操作日志。 */
  private final SystemLogService systemLogService;

  /**
   * 根据日志ID、请求ID、操作类型或状态查询系统日志。
   *
   * @param queryDTO 系统日志查询条件和最大返回数量
   * @return 满足条件的系统日志列表
   */
  @Operation(summary = "查询系统日志", description = "支持按日志ID、请求ID、操作类型与状态组合查询")
  @PostMapping("/query")
  public ApiResponse<List<SystemLogVO>> queryLogs(
      @Valid @RequestBody SystemLogQueryDTO queryDTO) {
    return ApiResponse.success(
        systemLogService.queryLogs(
            queryDTO.getId(),
            queryDTO.getRequestId(),
            queryDTO.getOperationType() == null
                ? null
                : queryDTO.getOperationType().name(),
            queryDTO.getStatus(),
            queryDTO.getLimit()));
  }
}
