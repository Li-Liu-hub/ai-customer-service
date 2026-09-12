package com.knowledgeagent.common.aop;

import com.knowledgeagent.systemlog.pojo.OperationType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记需要由系统日志切面统一记录的业务方法。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

  /**
   * 指定当前方法对应的业务操作类型。
   *
   * @return 系统日志中的操作类型
   */
  OperationType type();

  /**
   * 指定生成operation_detail JSON对象的SpEL表达式。
   *
   * @return 可以访问方法参数和result变量的SpEL表达式
   */
  String detail() default "";
}
