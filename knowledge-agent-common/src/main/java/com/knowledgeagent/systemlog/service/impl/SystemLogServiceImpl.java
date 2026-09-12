package com.knowledgeagent.systemlog.service.impl;

import com.knowledgeagent.common.aop.OperationLog;
import com.knowledgeagent.systemlog.mapper.SystemLogMapper;
import com.knowledgeagent.systemlog.pojo.OperationType;
import com.knowledgeagent.systemlog.pojo.entity.SystemLog;
import com.knowledgeagent.systemlog.pojo.vo.SystemLogVO;
import com.knowledgeagent.systemlog.service.SystemLogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 实现系统日志的条件查询。 */
@Service
@RequiredArgsConstructor
public class SystemLogServiceImpl implements SystemLogService {

  /** 从系统日志表读取操作记录。 */
  private final SystemLogMapper systemLogMapper;

  /**
   * 根据日志ID、请求ID、操作类型或状态查询日志。
   *
   * @param id 日志ID；为空时不限制
   * @param requestId 请求ID；为空时不限制
   * @param operationType 操作类型；为空时不限制
   * @param status 操作状态；为空时不限制
   * @param limit 最大返回数量；为空时默认返回50条
   * @return 满足条件的系统日志列表
   */
  @Override
  @OperationLog(
      type = OperationType.QUERY_LOGS,
      detail = "{'logId': #id, 'requestId': #requestId}")
  public List<SystemLogVO> queryLogs(
      Long id,
      String requestId,
      String operationType,
      String status,
      Integer limit) {
    int resultLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
    return systemLogMapper
        .selectLogs(id, requestId, operationType, status, resultLimit)
        .stream()
        .map(this::toVO)
        .toList();
  }

  /**
   * 将数据库日志实体转换为接口和Agent工具使用的返回对象。
   *
   * @param systemLog 从数据库查询到的日志实体
   * @return 不包含持久化实现细节的日志返回对象
   */
  private SystemLogVO toVO(SystemLog systemLog) {
    return SystemLogVO.builder()
        .id(systemLog.getId())
        .requestId(systemLog.getRequestId())
        .operationType(systemLog.getOperationType())
        .operationDetail(systemLog.getOperationDetail())
        .latencyMs(systemLog.getLatencyMs())
        .promptTokens(systemLog.getPromptTokens())
        .completionTokens(systemLog.getCompletionTokens())
        .modelName(systemLog.getModelName())
        .status(systemLog.getStatus())
        .errorCode(systemLog.getErrorCode())
        .errorReason(systemLog.getErrorReason())
        .createTime(systemLog.getCreateTime())
        .build();
  }
}
