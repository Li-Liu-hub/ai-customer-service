package com.knowledgeagent.systemlog.mapper;

import com.knowledgeagent.systemlog.pojo.entity.SystemLog;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 定义系统日志表的查询操作。 */
@Mapper
public interface SystemLogMapper {

  /**
   * 新增一条系统操作日志。
   *
   * @param systemLog 要保存的完整系统日志
   * @return 受影响的记录数
   */
  int insert(SystemLog systemLog);

  /**
   * 根据可选条件查询系统日志表。
   *
   * @param id 日志ID；为空时不限制
   * @param requestId 请求ID；为空时不限制
   * @param operationType 操作类型；为空时不限制
   * @param status 操作状态；为空时不限制
   * @param limit 最大返回数量
   * @return 满足条件的系统日志列表
   */
  List<SystemLog> selectLogs(
      @Param("id") Long id,
      @Param("requestId") String requestId,
      @Param("operationType") String operationType,
      @Param("status") String status,
      @Param("limit") Integer limit);
}
