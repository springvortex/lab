package com.zjc.demo.feature.version;

import com.zjc.demo.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 版本管理示例（Spring Boot 4.0 新能力）。
 *
 * <p>
 * <b>以前怎么做：</b>同一个接口要出新版本，只能靠约定俗成的土办法——
 * 路径里塞 {@code /v1/}、{@code /v2/}，或者干脆复制一个 Controller 类。
 * 结果是 URL 里混进版本号（版本升级变成「改地址」，客户端全得跟着改），
 * 或者两个类里 90% 的代码重复。
 *
 * <p>
 * <b>现在怎么做：</b>URL <b>保持不变</b>，只在 {@code @GetMapping} 上用 {@code version}
 * 声明版本；版本号从请求头 {@code X-API-Version} 读取（由
 * {@code spring.mvc.apiversion.use.header} 指定）。同一个路径、
 * 同一个方法、两个版本各写一个处理方法，Spring 按请求头自动路由。
 *
 * <pre>{@code
 * GET /api/version-demo/orders/A001          # 不带版本头 -> 落到默认版本 1.0
 *   请求头 X-API-Version: 1.0
 *   -> {"data":{"orderId":"A001","amount":"199.00"}}
 *   请求头 X-API-Version: 2.0
 *   -> {"data":{"orderId":"A001","amount":"199.00","currency":"CNY","status":"PAID"}}
 * }</pre>
 *
 * <p>
 * <b>注意事项：</b>
 * <ul>
 * <li>版本号是字符串，比较按解析后的语义版本进行（默认
 * {@code SemanticApiVersionParser}），所以 {@code 1.10} 大于 {@code 1.9}，
 * 不是字符串比较；</li>
 * <li><b>必须配置 {@code spring.mvc.apiversion.default}</b>。实测：只配
 * {@code use.header} 而不给默认版本时，凡是没带版本头的请求都会抛
 * {@code MissingApiVersionException}，连 {@code /hello} 这种没版本化的接口都会变成 500。
 * 给了默认版本后，老客户端与未版本化接口都照常工作；</li>
 * <li>请求了 {@code supported} 清单之外的版本会返回 400，而不是静默降级到低版本，
 * 这样调用方能立刻发现版本写错了。</li>
 * </ul>
 *
 * @author jiancai.zhong
 */
@RestController
@RequestMapping("/api/version-demo/orders")
public class VersionedOrderController {

    /**
     * v1 版本：只返回订单号和金额。
     *
     * @param orderId 订单号
     * @return 统一响应封装，{@code data} 为 v1 结构
     */
    @GetMapping(path = "/{orderId}", version = "1.0")
    public ApiResponse<OrderV1> getOrderV1(@PathVariable String orderId) {
        return ApiResponse.success(new OrderV1(orderId, "199.00"));
    }

    /**
     * v2 版本：同样是 {@code /api/version-demo/orders/{orderId}}，返回体多了币种与状态。
     *
     * <p>
     * 两个方法路径完全相同，只有 {@code version} 不同——这正是 API 版本管理要解决的问题：
     * <b>版本演进不再需要改 URL</b>。
     *
     * @param orderId 订单号
     * @return 统一响应封装，{@code data} 为 v2 结构
     */
    @GetMapping(path = "/{orderId}", version = "2.0")
    public ApiResponse<OrderV2> getOrderV2(@PathVariable String orderId) {
        return ApiResponse.success(new OrderV2(orderId, "199.00", "CNY", "PAID"));
    }

    /**
     * v1 版本的返回体。
     *
     * @param orderId 订单号
     * @param amount  金额
     */
    public record OrderV1(String orderId, String amount) {
    }

    /**
     * v2 版本的返回体：在 v1 基础上新增 {@code currency} 与 {@code status}。
     *
     * @param orderId  订单号
     * @param amount   金额
     * @param currency 币种
     * @param status   订单状态
     */
    public record OrderV2(String orderId, String amount, String currency, String status) {
    }
}
