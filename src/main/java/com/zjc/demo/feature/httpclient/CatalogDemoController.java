package com.zjc.demo.feature.httpclient;

import com.zjc.demo.web.ApiResponse;
import jakarta.annotation.Resource;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 声明式 HTTP 客户端的可运行演示：自己既是「上游」又假装是「下游」。
 *
 * <p>
 * 之所以做成自调用，是为了让示例<b>不依赖任何外部服务</b>就能跑通：
 * <ol>
 * <li>{@code GET /demo/catalog/{id}} —— 扮演下游服务（假装是另一个系统），
 * 直接返回商品数据；</li>
 * <li>{@code GET /demo/catalog-proxy/{id}} —— 通过 {@link CatalogClient} 发起真实 HTTP
 * 调用，打到上一步的地址。</li>
 * </ol>
 *
 * <p>
 * 启动应用后先访问第二个地址，日志里能看到两条 traceId 相同的请求记录
 * （出站透传由 {@code TraceIdPropagationInterceptor} 完成），
 * 这就是「声明式客户端 + 链路透传」的实际效果。
 *
 * <p>
 * <b>注意「下游」接口的返回体：</b>它刻意<b>不</b>包 {@link ApiResponse}。
 * 真实场景里下游是别人的系统，返回结构由对方决定，你只能按对方的格式声明客户端接口——
 * 这也正是声明式客户端要解决的问题：把「对方的契约」写清楚，而不是在业务代码里手工解析。
 *
 * @author jiancai.zhong
 */
@RestController
public class CatalogDemoController {

    /**
     * 声明式 HTTP 客户端代理，由 {@code @ImportHttpServices} 注册成 Bean。
     */
    @Resource
    private CatalogClient catalogClient;

    /**
     * 最近一次查询到的商品，用于演示 {@code @Nullable} 的用法。
     *
     * <p>
     * 本包标了 {@code @NullMarked}，成员默认非空；这里确实可能为空（还没查过），
     * 因此必须显式标注 {@link Nullable}，否则静态分析会把它当成「永不为空」而产生误判。
     */
    private @Nullable CatalogItem lastQueried;

    /**
     * 扮演下游服务：按 ID 返回商品。
     *
     * <p>
     * 和项目里其他接口不同，这里直接返回 DTO 而不是 {@link ApiResponse}，
     * 因为它在演示中代表「别人的接口」。
     *
     * @param id 商品 ID
     * @return 商品信息，返回体形如 {@code {"id":"A001","name":"机械键盘","price":"399.00"}}
     */
    @GetMapping("/demo/catalog/{id}")
    public CatalogItem downstream(@PathVariable String id) {
        CatalogItem item = new CatalogItem(id, "机械键盘", "399.00");
        lastQueried = item;
        return item;
    }

    /**
     * 扮演上游：通过声明式客户端调用上一步的接口。
     *
     * @param id 商品 ID
     * @return 统一响应封装，{@code data} 为下游返回的商品信息
     */
    @GetMapping("/demo/catalog-proxy/{id}")
    public ApiResponse<CatalogItem> viaDeclarativeClient(@PathVariable String id) {
        return ApiResponse.success(catalogClient.findById(id));
    }

    /**
     * 返回最近一次查询结果，演示 {@code @Nullable} 的边界：没查过时 {@code data} 为 {@code null}。
     *
     * @return 统一响应封装，{@code data} 可能为 {@code null}
     */
    @GetMapping("/demo/catalog/last-queried")
    public ApiResponse<@Nullable CatalogItem> lastQueried() {
        return ApiResponse.success(lastQueried);
    }
}
