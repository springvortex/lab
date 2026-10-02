package com.zjc.demo.feature.jackson;

import com.zjc.demo.web.ApiResponse;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Jackson 3 读/写特性配置示例（Spring Boot 4.1 新能力）。
 *
 * <p>
 * Boot 4.1 起，跨格式（JSON / CBOR / XML）通用的 Jackson 读写开关可以用配置项声明，
 * 不必再写 Java 定制类：
 *
 * <pre>{@code
 * spring:
 *   jackson:
 *     read:                                   # 绑定到 StreamReadFeature
 *       strict-duplicate-detection: true      # 重复字段直接拒绝
 *     write:                                  # 绑定到 StreamWriteFeature
 *       write-bigdecimal-as-plain: true       # BigDecimal 不用科学计数法
 *     factory:
 *       constraints:                          # 解析器级硬约束，防解析型攻击
 *         read:
 *           max-nesting-depth: 1000
 *           max-string-length: 100000000
 * }</pre>
 *
 * <p>
 * <b>为什么「重复字段」值得单独开一个开关：</b>
 * {@code {"amount":10,"amount":9999}} 这种报文，不同 JSON 库取值规则不同——
 * 有的取先出现的，有的取后出现的。如果网关按第一种解析、业务代码按第二种解析，
 * 攻击者就能构造出一个「网关看到 10、实际扣款 9999」的报文。
 * 打开严格重复检测后直接拒绝，从源头上消除这种歧义。
 *
 * <p>
 * <b>为什么 {@code max-nesting-depth} 有用：</b>深度嵌套的 JSON 会让解析器递归栈爆掉，
 * 是典型的「用一个请求打挂服务」手法。给解析器一个上限，比在业务代码里逐个判断靠谱。
 *
 * @author jiancai.zhong
 */
@RestController
@RequestMapping("/demo/jackson")
public class JacksonDemoController {

    /**
     * 回显请求体，用于观察 Jackson 的序列化行为。
     *
     * <pre>{@code
     * POST /demo/jackson/echo
     * {"orderId":"A001","amount":1.0E+7}
     * -> {"data":{"orderId":"A001","amount":10000000}}   # 开启 write-bigdecimal-as-plain 后不是 1.0E+7
     * }</pre>
     *
     * @param payload 请求体，含一个 {@link BigDecimal} 金额字段
     * @return 统一响应封装，{@code data} 为原样回显的请求体
     */
    @PostMapping("/echo")
    public ApiResponse<MoneyPayload> echo(@RequestBody MoneyPayload payload) {
        return ApiResponse.success(payload);
    }

    /**
     * 演示用的金额报文。
     *
     * @param orderId 订单号
     * @param amount  金额，用 {@link BigDecimal} 承载，避免浮点误差
     */
    public record MoneyPayload(String orderId, BigDecimal amount) {
    }
}
