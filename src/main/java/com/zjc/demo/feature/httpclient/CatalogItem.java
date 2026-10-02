package com.zjc.demo.feature.httpclient;

/**
 * 商品条目，用来演示声明式 HTTP 客户端的请求/响应体映射。
 *
 * <p>
 * 用 record 而不是 Lombok 类：它是纯数据载体，不可变、只需要构造器与访问器，
 * Jackson 3 原生支持 record（无需无参构造器，也不需要额外注解）。
 *
 * @param id    商品 ID
 * @param name  商品名称
 * @param price 商品价格，用字符串避免浮点精度问题
 * @author jiancai.zhong
 */
public record CatalogItem(String id, String name, String price) {
}
