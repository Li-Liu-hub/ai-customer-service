package com.knowledgeagent.common.aop;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgeagent.common.exception.BusinessException;
import com.knowledgeagent.common.aop.OperationLogContext.ModelMetrics;
import com.knowledgeagent.systemlog.mapper.SystemLogMapper;
import com.knowledgeagent.systemlog.pojo.entity.SystemLog;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 统一记录业务方法的影响数据、耗时、模型消耗和错误结果。 */
@Aspect
@Component
@RequiredArgsConstructor
public class SystemLogAspect {

  /** 无请求ID请求在当前HttpServletRequest中使用的属性名称。 */
  private static final String REQUEST_ID_ATTRIBUTE = "systemLogRequestId";

  /** 读取调用方主动传入请求ID时使用的请求头。 */
  private static final String REQUEST_ID_HEADER = "X-Request-ID";

  /** 解析注解中操作详情表达式。 */
  private static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

  /** 记录日志入库失败等不应该中断业务的内部错误。 */
  private static final Logger LOGGER = LoggerFactory.getLogger(SystemLogAspect.class);

  /** 将操作日志写入系统日志表。 */
  private final SystemLogMapper systemLogMapper;

  /** 把受影响ID等操作详情转换为JSON。 */
  private final ObjectMapper objectMapper;

  /**
   * 环绕执行带有OperationLog注解的业务方法并统一写入成功或失败日志。
   *
   * @param joinPoint 当前被记录的业务方法
   * @param operationLog 方法声明的操作类型和详情表达式
   * @return 原业务方法执行结果
   * @throws Throwable 原业务方法抛出的任何异常
   */
  @Around("@annotation(operationLog)")
  public Object recordOperation(
      ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
    long startNanos = System.nanoTime();
    Object result = null;
    Throwable failure = null;
    OperationLogContext.start();

    try {
      result = joinPoint.proceed();
      return result;
    } catch (Throwable throwable) {
      failure = throwable;
      throw throwable;
    } finally {
      ModelMetrics metrics = OperationLogContext.snapshot();
      OperationLogContext.clear();
      saveLog(
          joinPoint,
          operationLog,
          result,
          failure,
          metrics,
          elapsedMillis(startNanos));
    }
  }

  /**
   * 构建完整系统日志并尝试写入数据库，日志失败不会改变原业务结果。
   *
   * @param joinPoint 当前业务方法及其参数
   * @param operationLog 操作日志注解
   * @param result 业务方法返回结果；失败时可能为空
   * @param failure 业务方法抛出的异常；成功时为空
   * @param metrics 本次操作累计的模型名称和Token数量
   * @param latencyMs 本次业务方法总耗时毫秒数
   */
  private void saveLog(
      ProceedingJoinPoint joinPoint,
      OperationLog operationLog,
      Object result,
      Throwable failure,
      ModelMetrics metrics,
      long latencyMs) {
    StandardEvaluationContext context = createEvaluationContext(joinPoint, result);
    String detail = resolveOperationDetail(operationLog.detail(), context);

    SystemLog systemLog =
        SystemLog.builder()
            .id(IdWorker.getId())
            .requestId(resolveRequestId())
            .operationType(operationLog.type().name())
            .operationDetail(detail)
            .latencyMs(latencyMs)
            .promptTokens(metrics.promptTokens())
            .completionTokens(metrics.completionTokens())
            .modelName(metrics.modelName())
            .status(failure == null ? "SUCCESS" : "FAILED")
            .errorCode(resolveErrorCode(failure))
            .errorReason(failure == null ? null : failure.getMessage())
            .createTime(OffsetDateTime.now())
            .build();

    try {
      systemLogMapper.insert(systemLog);
    } catch (RuntimeException logFailure) {
      LOGGER.error(
          "System log insert failed requestId={} operationType={} reason={}",
          systemLog.getRequestId(),
          systemLog.getOperationType(),
          logFailure.getMessage());
    }
  }

  /**
   * 创建能够读取方法参数和返回结果的SpEL上下文。
   *
   * @param joinPoint 当前业务方法及其参数
   * @param result 业务方法返回结果
   * @return 可供日志表达式使用的上下文
   */
  private StandardEvaluationContext createEvaluationContext(
      ProceedingJoinPoint joinPoint, Object result) {
    StandardEvaluationContext context = new StandardEvaluationContext();
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    String[] parameterNames = signature.getParameterNames();
    Object[] arguments = joinPoint.getArgs();
    if (parameterNames != null) {
      for (int index = 0; index < parameterNames.length; index++) {
        context.setVariable(parameterNames[index], arguments[index]);
      }
    }
    context.setVariable("result", result);
    return context;
  }

  /**
   * 计算操作详情表达式并序列化为JSON对象。
   *
   * @param expression 注解声明的SpEL表达式
   * @param context 当前方法的表达式上下文
   * @return 可以写入JSONB字段的JSON文本
   */
  private String resolveOperationDetail(
      String expression, StandardEvaluationContext context) {
    Object detail = null;
    if (expression != null && !expression.isBlank()) {
      try {
        detail = EXPRESSION_PARSER.parseExpression(expression).getValue(context);
      } catch (RuntimeException expressionFailure) {
        LOGGER.warn("Operation detail expression failed: {}", expressionFailure.getMessage());
      }
    }
    try {
      return objectMapper.writeValueAsString(detail == null ? Map.of() : detail);
    } catch (JsonProcessingException serializationFailure) {
      return "{}";
    }
  }

  /**
   * 获取或创建贯穿当前HTTP请求的请求ID。
   *
   * @return 当前请求ID；非HTTP调用时创建新的UUID
   */
  private String resolveRequestId() {
    if (!(RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes)) {
      return UUID.randomUUID().toString();
    }
    HttpServletRequest request = attributes.getRequest();
    Object existing = request.getAttribute(REQUEST_ID_ATTRIBUTE);
    if (existing != null) {
      return existing.toString();
    }
    String headerValue = request.getHeader(REQUEST_ID_HEADER);
    String requestId =
        headerValue == null || headerValue.isBlank()
            ? UUID.randomUUID().toString()
            : headerValue.strip();
    request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
    return requestId;
  }

  /**
   * 根据业务异常或未知异常生成日志错误码。
   *
   * @param failure 业务方法抛出的异常；成功时为空
   * @return 业务错误码、异常类型名称或null
   */
  private String resolveErrorCode(Throwable failure) {
    if (failure == null) {
      return null;
    }
    if (failure instanceof BusinessException businessException) {
      return String.valueOf(businessException.getCode());
    }
    return failure.getClass().getSimpleName();
  }

  /**
   * 将纳秒开始时间转换为当前已耗费的毫秒数。
   *
   * @param startNanos 方法执行前记录的纳秒时间
   * @return 已耗费的毫秒数
   */
  private long elapsedMillis(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }
}
