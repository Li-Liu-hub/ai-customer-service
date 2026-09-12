package com.knowledgeagent.systemlog.service;

import com.knowledgeagent.systemlog.pojo.vo.SystemLogVO;
import java.util.List;

/** 定义系统日志查询业务。 */
public interface SystemLogService {

  /**
   * 根据可选条件查询系统日志表。
   *
   * @param id 日志ID；为空时不限制
   * @param requestId 请求ID；为空时不限制
   * @param operationType 操作类型；为空时不限制
   * @param status 操作状态；为空时不限制
   * @param limit 最大返回数量；为空时使用默认值
   * @return 满足条件的系统日志列表
   */
  List<SystemLogVO> queryLogs(
      Long id,
      String requestId,
      String operationType,
      String status,
      Integer limit);
}
