package com.zjc.demo.support;

import com.zjc.demo.constant.ApiResponseConstant;
import com.zjc.demo.exception.BusinessException;
import com.zjc.demo.web.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
 * Spring 6.1 起 {@code @RequestParam} 上的约束注解改抛
 * {@code HandlerMethodValidationException}，其实际走向与直觉不同。
 *
 * @author jiancai.zhong
 */
@RestController
@RequestMapping("/test")
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
     * 参数上直接挂校验注解，用于观察 Spring 6.1+ 的方法参数校验走向。
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
