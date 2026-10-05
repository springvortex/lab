package com.zjc.demo.support;

import java.util.Map;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.zjc.demo.constant.ApiResponseConstant;
import com.zjc.demo.exception.BusinessException;
import com.zjc.demo.web.ApiResponse;

/**
 * 测试专用接口，用于从真实 HTTP 链路触发各类异常与正常返回。
 *
 * <p>
 * 放在 {@code src/test} 下即可被 {@code @SpringBootTest} 的组件扫描发现，因此它能参与
 * 完整 MVC 流程（Filter → DispatcherServlet → 拦截器 → AOP → Controller），
 * 使 {@code GlobalExceptionHandler} 与 {@code WebLogAspect} 走真实调用链而不是被直接调用。
 *
 * <p>
 * <b>为什么不用 Mockito 打桩异常再调用处理器方法：</b>那样只能验证「处理器拿到异常后返回什么」，
 * 验证不了「异常是否真的会被路由到这个处理器」。本模板最容易踩的坑恰恰是后者，例如
 * Spring 版本不同导致的「同一个请求返回完全不同的状态码」：Spring 6.1 起会自动校验
 * Controller 方法参数并抛 {@code HandlerMethodValidationException}，而 Spring 5（Boot 2）
 * 必须显式加 {@link Validated} 才校验，且抛的是 {@code ConstraintViolationException}。
 *
 * <p>
 * <b>为什么类上有 {@link Validated}：</b>它是 Spring 5 下开启「方法参数约束校验」的开关。
 * 加上后 {@code MethodValidationPostProcessor} 会给本类生成代理，方法入参上的
 * {@code @NotBlank} 才会在调用前生效。Boot 3 可以不加（框架内置），但加了同样兼容，
 * 因此模板保留它，让两个版本的脚手架行为一致。
 *
 * @author jiancai.zhong
 */
@RestController
@RequestMapping("/test")
@Validated
public class ExceptionTestController {

    /**
     * 正常返回，用于验证统一响应结构与 traceId 透传。
     *
     * @return 成功响应
     */
    @GetMapping("/ok")
    public ApiResponse<String> ok() {
        return ApiResponse.success("ok");
    }

    /**
     * 抛出自定义错误码的业务异常。
     *
     * @param code    错误码，传非法值可验证 HTTP 状态码兜底逻辑
     * @param message 错误提示
     * @return 不会正常返回，必定抛异常
     */
    @GetMapping("/business")
    public ApiResponse<Void> business(@RequestParam int code, @RequestParam String message) {
        throw new BusinessException(code, message);
    }

    /**
     * 抛出以枚举构造的业务异常。
     *
     * @return 不会正常返回，必定抛异常
     */
    @GetMapping("/business-enum")
    public ApiResponse<Void> businessEnum() {
        throw new BusinessException(ApiResponseConstant.CONFLICT);
    }

    /**
     * 抛出带 4xx 状态码与原因的 {@code ResponseStatusException}。
     *
     * <p>
     * 用来验证框架级「自带状态码的异常」不会被兜底改写成 500，且原因会透给调用方。
     *
     * @return 不会正常返回，必定抛异常
     */
    @GetMapping("/status-conflict")
    public ApiResponse<Void> statusConflict() {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "订单状态冲突");
    }

    /**
     * 抛出不带原因的 4xx {@code ResponseStatusException}，验证消息回退到状态码短语。
     *
     * @return 不会正常返回，必定抛异常
     */
    @GetMapping("/status-no-reason")
    public ApiResponse<Void> statusNoReason() {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    /**
     * 抛出 5xx 的 {@code ResponseStatusException}，验证服务端错误仍然隐藏细节。
     *
     * @return 不会正常返回，必定抛异常
     */
    @GetMapping("/status-server-error")
    public ApiResponse<Void> statusServerError() {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "模拟内部细节，不应出现在响应体里");
    }

    /**
     * 抛出未预期异常，验证兜底处理不会把堆栈泄露给调用方。
     *
     * @return 不会正常返回，必定抛异常
     */
    @GetMapping("/boom")
    public ApiResponse<Void> boom() {
        throw new IllegalStateException("模拟未预期异常，不应出现在响应体里");
    }

    /**
     * 缺少必填查询参数时抛 {@code MissingServletRequestParameterException}。
     *
     * @param name 必填参数
     * @return 成功响应
     */
    @GetMapping("/param")
    public ApiResponse<String> param(@RequestParam String name) {
        return ApiResponse.success(name);
    }

    /**
     * 参数上直接挂校验注解，用于观察 Spring 5 下方法参数校验的走向。
     *
     * <p>
     * 生效前提是类上的 {@link Validated}。校验失败时由 AOP 代理抛
     * {@code ConstraintViolationException}，其 {@code propertyPath} 形如
     * {@code 方法名.参数名}，因此最终提示会带上方法名（与 Boot 3 的纯参数名写法不同）。
     *
     * @param name 名称，不允许空白
     * @return 成功响应
     */
    @GetMapping("/validated-param")
    public ApiResponse<String> validatedParam(@RequestParam @NotBlank(message = "名称不能为空") String name) {
        return ApiResponse.success(name);
    }

    /**
     * 请求体校验，验证 {@code MethodArgumentNotValidException} 的处理。
     *
     * @param request 带校验约束的请求体
     * @return 成功响应
     */
    @PostMapping("/validate")
    public ApiResponse<String> validate(@Valid @RequestBody TestRequest request) {
        return ApiResponse.success(request.getName());
    }

    /**
     * 返回一个超出 JS 安全整数范围的 Long，验证全局 Long 转字符串配置。
     *
     * @return 含 Long 字段的成功响应
     */
    @GetMapping("/long-value")
    public ApiResponse<Map<String, Long>> longValue() {
        return ApiResponse.success(Map.of("id", 1234567890123456789L));
    }

    /**
     * 返回大体积字符串，验证切面对返回值的截断逻辑。
     *
     * @return 长度为 3000 的字符串
     */
    @GetMapping("/big-result")
    public ApiResponse<String> bigResult() {
        return ApiResponse.success("x".repeat(3000));
    }
}
