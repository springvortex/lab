package com.zjc.demo.core.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import com.zjc.demo.core.exception.BusinessException;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * Web 接口日志切面，自动记录 Controller 层的请求入参、返回结果与执行耗时。
 *
 * <p>
 * 拦截所有标注了 {@code @RestController} 的类，对每个方法：
 * <ul>
 * <li>请求前：记录 HTTP 方法、URI、类名、方法名、入参</li>
 * <li>正常返回：记录耗时与返回值（超长截断）</li>
 * <li>抛出异常：业务异常记录 WARN，未预期异常记录 ERROR，异常继续向上抛出</li>
 * </ul>
 *
 * <p>
 * 标注 {@code @Component}，由组件扫描注册为 Spring Bean 后生效。
 *
 * <p>
 * <b>注意事项：</b>
 * <ul>
 * <li>切点只覆盖标注 {@code @RestController} 的类，用 {@code @Controller} 写的页面控制器
 * 不会被记录；</li>
 * <li>{@link #formatArgs(Object[])} <b>不截断</b>入参，密码、手机号、证件号等敏感字段会被
 * 原样写入日志。含敏感字段的接口请在此处补充脱敏，或改用 {@code @ToString.Exclude} 等手段 控制序列化内容；</li>
 * <li>入参或返回值过大时序列化本身有开销，高 QPS 接口可考虑改为按需打印或采样；</li>
 * <li>异常会原样向上抛出，本切面只负责记录，不改变原有异常传播链路。</li>
 * </ul>
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
	 * 环绕增强：记录请求入参、执行耗时与返回结果。
	 *
	 * @param joinPoint 连接点
	 * @return 目标方法的返回值
	 * @throws Throwable 目标方法抛出的异常，原样向上传递
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
	 * 从当前线程上下文获取 HttpServletRequest。
	 *
	 * @return 当前请求，非 HTTP 调用时返回 {@code null}
	 */
	private HttpServletRequest getRequest() {
		ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		return attrs != null ? attrs.getRequest() : null;
	}

	/**
	 * 格式化方法入参数组为 JSON 字符串。
	 *
	 * <p>
	 * 跳过
	 * {@code HttpServletRequest}、{@code HttpServletResponse}、{@code MultipartFile}
	 * 等不适合序列化的对象，仅记录类型名。
	 *
	 * <p>
	 * 与 {@link #formatResult(Object)} 不同，入参<b>不做长度截断</b>：完整入参是排查问题的
	 * 关键依据。若某个接口入参极大，请评估日志体积后决定是否在此加限制。
	 *
	 * @param args 入参数组
	 * @return JSON 格式的入参字符串，无参时返回 {@code []}
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
	 * 格式化单个对象为 JSON 字符串，非序列化对象记录类型名。
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
	 * 格式化返回值，超长截断并追加省略标记。
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
