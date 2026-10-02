package com.zjc.demo.controller;

import com.zjc.demo.service.HelloService;
import com.zjc.demo.web.ApiResponse;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 示例接口，演示 Controller 层的标准写法；派生新项目时可直接删除。
 *
 * <p>
 * 新写的 Controller 请照此结构，三条模板约定：
 * <ol>
 * <li>只做「接收参数 → 调用 Service → 包装响应」，不写业务逻辑；</li>
 * <li>返回值统一用 {@link ApiResponse} 包装，不直接返回业务实体，否则前端拿到的结构
 * 会与其他接口不一致；</li>
 * <li>不要写 try-catch，异常交给 {@code GlobalExceptionHandler} 统一处理，
 * 否则自定义的错误结构会绕过「code 即 HTTP 状态码」的约定。</li>
 * </ol>
 *
 * <p>
 * 入参与返回值的日志由 {@code WebLogAspect} 自动记录，无需手动打日志。
 *
 * @author jiancai.zhong
 */
@RestController
public class HelloController {

    /**
     * 示例业务服务。
     *
     * <p>
     * 本项目统一使用 {@code @Resource}（按名称注入）而非 {@code @Autowired}（按类型注入），
     * 这样同一接口存在多个实现时，可通过字段名精确指定目标 Bean。
     */
    @Resource
    private HelloService demoService;

    /**
     * 返回一句问候语，用于验证「HTTP 请求 → Service → 统一响应」链路是否打通。
     *
     * <p>
     * 请求与响应示例：
     *
     * <pre>{@code
     * GET /hello
     * 200
     * {"success":true,"code":200,"message":"操作成功","data":"hello SpringVortexDemo!","traceId":"..."}
     * }</pre>
     *
     * @return 统一响应封装，{@code data} 为问候内容
     */
    @GetMapping("/hello")
    public ApiResponse<String> hello() {
        return ApiResponse.success(demoService.hello());
    }
}
