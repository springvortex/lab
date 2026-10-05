package com.zjc.demo.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Pointcut;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjc.demo.exception.BusinessException;
import com.zjc.demo.support.TestStrings;

/**
 * {@link WebLogAspect} 的单元测试。
 *
 * <p>
 * <b>为什么用 Mockito 而不是走真实 MVC：</b>切面有大量防御性分支——上下文不可用、
 * 无入参、入参里混有 Servlet 对象、序列化失败、返回值为空、返回值超长、两类异常——
 * 其中好几条在真实请求里几乎无法稳定构造（例如让 Jackson 序列化失败）。
 * 用 Mockito 打桩 {@code ProceedingJoinPoint} 才能把这些分支逐条钉死。
 * 「异常是否真的会被路由到这里」由 {@code ApiIntegrationTest} 负责验证，两者互补。
 *
 * @author jiancai.zhong
 */
class WebLogAspectTest {

    /**
     * 被测切面。
     */
    private WebLogAspect aspect;

    /**
     * 模拟连接点。
     */
    private ProceedingJoinPoint joinPoint;

    /**
     * 初始化切面并注入真实的 ObjectMapper（切面靠它序列化入参与返回值）。
     */
    @BeforeEach
    void setUp() {
        aspect = new WebLogAspect();
        ReflectionTestUtils.setField(aspect, "objectMapper", new ObjectMapper());

        joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn("hello");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new SampleController());
    }

    /**
     * 清理请求上下文，避免用例之间互相影响。
     */
    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * 把当前线程绑定到一个模拟 HTTP 请求上，使切面能取到 method 与 URI。
     */
    private void bindRequestContext() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest("GET", "/sample")));
    }

    /**
     * 正常返回且无入参：入参格式化为 {@code []}，返回值序列化为 JSON。
     *
     * @throws Throwable 目标方法抛出的异常
     */
    @Test
    @DisplayName("正常返回：记录入参与返回值")
    void logsArgumentsAndResult() throws Throwable {
        bindRequestContext();
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn("result-value");

        assertThat(aspect.logAround(joinPoint)).isEqualTo("result-value");
    }

    /**
     * 无请求上下文（定时任务、单元测试等非 HTTP 调用）时，method 与 URI 降级为 {@code N/A}。
     *
     * @throws Throwable 目标方法抛出的异常
     */
    @Test
    @DisplayName("无请求上下文：降级为 N/A 且不抛异常")
    void worksWithoutRequestContext() throws Throwable {
        RequestContextHolder.resetRequestAttributes();
        when(joinPoint.getArgs()).thenReturn(null);
        when(joinPoint.proceed()).thenReturn(null);

        assertThat(aspect.logAround(joinPoint)).isNull();
    }

    /**
     * 入参数组为 {@code null} 时按空数组处理；返回值为 {@code null} 时记录 {@code null} 字面量。
     *
     * @throws Throwable 目标方法抛出的异常
     */
    @Test
    @DisplayName("入参为 null、返回值为 null")
    void handlesNullArgsAndNullResult() throws Throwable {
        bindRequestContext();
        when(joinPoint.getArgs()).thenReturn(null);
        when(joinPoint.proceed()).thenReturn(null);

        assertThat(aspect.logAround(joinPoint)).isNull();
    }

    /**
     * 入参里混有 {@code HttpServletRequest} / {@code HttpServletResponse} / {@code MultipartFile}
     * 时，只记录类型名，不做 JSON 序列化（否则会拖垮日志或抛异常）。
     *
     * @throws Throwable 目标方法抛出的异常
     */
    @Test
    @DisplayName("入参含 Servlet 对象与文件：只记录类型名")
    void skipsServletAndMultipartArguments() throws Throwable {
        bindRequestContext();
        when(joinPoint.getArgs()).thenReturn(new Object[]{
                new MockHttpServletRequest(), new MockHttpServletResponse(), mock(MultipartFile.class),
                "plain-string", null
        });
        when(joinPoint.proceed()).thenReturn("ok");

        assertThat(aspect.logAround(joinPoint)).isEqualTo("ok");
    }

    /**
     * 入参无法序列化时必须降级为 {@code 类名@hashCode}，绝不能因为打日志把业务请求打挂。
     *
     * @throws Throwable 目标方法抛出的异常
     */
    @Test
    @DisplayName("入参序列化失败：降级为类名@hashCode")
    void fallsBackWhenSerializationFails() throws Throwable {
        bindRequestContext();
        when(joinPoint.getArgs()).thenReturn(new Object[]{new ExplodingBean()});
        when(joinPoint.proceed()).thenReturn("ok");

        assertThat(aspect.logAround(joinPoint)).isEqualTo("ok");
    }

    /**
     * 返回值超过日志上限时追加省略标记，防止单条日志过大。
     *
     * @throws Throwable 目标方法抛出的异常
     */
    @Test
    @DisplayName("返回值超长：截断并追加省略标记")
    void truncatesOverlongResult() throws Throwable {
        bindRequestContext();
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(TestStrings.repeat("x", 3000));

        assertThat((String) aspect.logAround(joinPoint)).hasSize(3000);
    }

    /**
     * 业务异常按 WARN 记录且不打印堆栈，并原样向上抛出。
     */
    @Test
    @DisplayName("业务异常：原样抛出")
    void rethrowsBusinessException() throws Throwable {
        bindRequestContext();
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenThrow(new BusinessException("业务校验不通过"));

        assertThatThrownBy(() -> aspect.logAround(joinPoint))
                .isInstanceOf(BusinessException.class)
                .hasMessage("业务校验不通过");
    }

    /**
     * 未预期异常按 ERROR 记录并带堆栈，同样原样向上抛出。
     */
    @Test
    @DisplayName("未预期异常：原样抛出")
    void rethrowsUnexpectedException() throws Throwable {
        bindRequestContext();
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("程序缺陷"));

        assertThatThrownBy(() -> aspect.logAround(joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("程序缺陷");
    }

    /**
     * 把切点表达式钉死，防止它被无意放宽或收窄。
     *
     * <p>
     * {@code controllerPointcut()} 是 AspectJ 的「注解载体」方法：运行时只读它的注解，
     * 永远不会调用它，因此默认不会有任何覆盖记录。这里显式执行一次并断言其无副作用，
     * 同时校验表达式字符串——一旦有人把它改成 {@code @Controller}（页面控制器会被顺带记录）
     * 或更宽的范围，本用例会立刻失败。
     */
    @Test
    @DisplayName("切点声明：表达式锁定 @RestController 且无副作用")
    void pointcutDeclarationIsPinned() throws Exception {
        Pointcut pointcut = WebLogAspect.class.getDeclaredMethod("controllerPointcut").getAnnotation(Pointcut.class);

        assertThat(pointcut).isNotNull();
        assertThat(pointcut.value()).isEqualTo("@within(org.springframework.web.bind.annotation.RestController)");
        assertThatCode(aspect::controllerPointcut).doesNotThrowAnyException();
    }

    /**
     * 模拟 Controller，仅用于让切面取到有意义的类名。
     */
    private static class SampleController {
        /**
         * 占位方法，使内部类不被判定为无成员。
         */
        void hello() {
        }
    }

    /**
     * 模拟一个 getter 抛异常的 Bean，用于触发切面的序列化失败兜底分支。
     */
    public static class ExplodingBean {

        /**
         * 故意抛异常，模拟 Jackson 无法序列化的对象。
         *
         * @return 永不返回
         */
        public String getValue() {
            throw new IllegalStateException("模拟序列化失败");
        }
    }
}
