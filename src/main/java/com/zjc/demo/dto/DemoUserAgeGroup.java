package com.zjc.demo.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 年龄段统计结果，自定义 SQL 的返回载体（VO）。
 *
 * <p>
 * 它不对应任何一张表，只是 {@code GROUP BY} 结果的形状，因此没有 {@code @TableName}，也不该被当成实体用——放进
 * {@code dto} 包而非 {@code entity} 包就是这个意思。
 *
 * <p>
 * <b>字段命名与列的映射：</b>SQL 里写的是 {@code age_group} / {@code user_count}， 这里是
 * {@code ageGroup} / {@code userCount}，靠
 * {@code mybatis-plus.configuration.map-underscore-to-camel-case=true}（默认开启）自动对应。
 * 该开关被关掉时，<b>不会报错</b>，只会安静地映射成 {@code null}——排查时先看这个配置。
 *
 * <p>
 * <b>别名：</b>{@code application-db.yaml} 的
 * {@code mybatis-plus.type-aliases-package} 同时登记了 {@code entity} 与 {@code dto}
 * 两个包，所以 XML 里可以直接写短名 {@code DemoUserAgeGroup}；不登记就得写全限定名。
 *
 * @author jiancai.zhong
 */
@Data
public class DemoUserAgeGroup implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * 年龄段名称：未成年 / 青年 / 中年 / 老年，由 SQL 的 {@code CASE WHEN} 算出。
	 */
	private String ageGroup;

	/**
	 * 该年龄段的人数，对应 {@code COUNT(*)}。
	 *
	 * <p>
	 * 聚合函数返回的是 {@code bigint}，故用 {@code Long}；写成 {@code Integer} 在数据量大时会溢出。
	 *
	 * <p>
	 * <b>注意会被序列化成字符串：</b>项目的 Jackson 全局开了 {@code Long → String} （为防 19 位雪花 ID 在 JS
	 * 里被截断），这个计数也一并受影响，接口返回的是 {@code "userCount": "3"} 而不是
	 * {@code "userCount": 3}。前端要参与运算请先 {@code Number(...)} 转换。真想让计数字段保持数字类型，把它改成
	 * {@code Integer} 即可。
	 */
	private Long userCount;
}
