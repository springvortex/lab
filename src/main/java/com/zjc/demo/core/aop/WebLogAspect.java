package com.zjc.demo.core.aop;

import com.zjc.demo.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import tools.jackson.databind.json.JsonMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

/**
 * Web 接口日志切面：记录 {@code @RestController} 方法的入参、耗时与返回值。
 *
 * <p>
 * 入参<b>不做截断也不脱敏</b>，密码、手机号等敏感字段会被原样写进日志，敏感接口需自行处理。
 *
 * @author jiancai.zhong
 */
@Slf4j
@Aspect
@Component
public class WebLogAspect {

    /**
     * 返回值日志最大长度，超出部分截断
     */
    private static final int MAX_LOG_LENGTH = 2000;
    /**
     * 上下文信息不可用时的占位符
     */
    private static final String NOT_AVAILABLE = "N/A";
    /**
     * JSON 序列化中的 null 字面量
     */
    private static final String NULL_VALUE = "null";
    @Resource
    private JsonMapper jsonMapper;

    /**
     * 匹配所有 {@code @RestController} 类的公共方法。
     */
    @Pointcut("@within(org.springframework.web.bind.annotation.RestController)")
    public void controllerPointcut() {
    }

    /**
     * 环绕增强：记录请求入参、耗时与返回值，异常原样向上抛出。
     *
     * @param joinPoint 连接点
     * @return 目标方法的返回值
     * @throws Throwable 目标方法抛出的异常
     */
    @Around("controllerPointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = getRequest();
        String httpMethod = request != null ? request.getMethod() : NOT_AVAILABLE;
        String uri = request != null ? request.getRequestURI() : NOT_AVAILABLE;
        String target = joinPoint.getTarget().getClass().getSimpleName() + "." + joinPoint.getSignature().getName()
                + "()";

        log.info("==> {} {} | {} | args={}", httpMethod, uri, target, formatArgs(joinPoint.getArgs()));

        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long costTime = System.currentTimeMillis() - startTime;
            log.info("<== {} {} | {} | cost={}ms | result={}", httpMethod, uri, target, costTime, formatResult(result));
            return result;
        } catch (Throwable e) {
            long costTime = System.currentTimeMillis() - startTime;
            if (e instanceof BusinessException) {
                log.warn("<== {} {} | {} | cost={}ms | businessError={}", httpMethod, uri, target, costTime,
                        e.getMessage());
            } else {
                log.error("<== {} {} | {} | cost={}ms | error={}", httpMethod, uri, target, costTime, e.getMessage(),
                        e);
            }
            throw e;
        }
    }

    /**
     * 从当前线程上下文获取请求，非 HTTP 调用返回 {@code null}。
     *
     * @return 当前请求
     */
    private HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    /**
     * 入参格式化为 JSON。跳过不适合序列化的对象，仅记录类型名；入参不截断。
     *
     * @param args 入参数组
     * @return JSON 字符串
     */
    private String formatArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(formatObject(args[i]));
        }
        return sb.append("]").toString();
    }

    /**
     * 单个对象序列化为 JSON，不可序列化的对象退化为类型名。
     *
     * @param obj 待格式化的对象
     * @return JSON 字符串或类型名
     */
    private String formatObject(Object obj) {
        if (obj == null) {
            return NULL_VALUE;
        }
        if (obj instanceof HttpServletRequest || obj instanceof HttpServletResponse || obj instanceof MultipartFile) {
            return obj.getClass().getSimpleName();
        }
        try {
            return jsonMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.getClass().getSimpleName() + "@" + Integer.toHexString(obj.hashCode());
        }
    }

    /**
     * 返回值格式化，超长截断。
     *
     * @param result 返回值
     * @return 截断后的 JSON 字符串
     */
    private String formatResult(Object result) {
        if (result == null) {
            return NULL_VALUE;
        }
        String json = formatObject(result);
        if (json.length() > MAX_LOG_LENGTH) {
            return json.substring(0, MAX_LOG_LENGTH) + "...(truncated)";
        }
        return json;
    }
}
