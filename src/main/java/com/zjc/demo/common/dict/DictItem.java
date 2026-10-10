package com.zjc.demo.common.dict;

/**
 * 字典项契约。枚举实现本接口后会被 {@code DictRegistry} 自动收录，通过字典接口暴露给前端。
 *
 * <p>
 * 枚举的 {@code value} 用 {@code int} 而非 {@code Integer}，才能被 Lombok 生成的
 * {@code getValue()} 直接满足——{@code int} 与 {@code Integer} 不构成方法重写的匹配。
 *
 * @author jiancai.zhong
 */
public interface DictItem {

	/**
	 * 存库数值。
	 *
	 * @return 与数据库列对应的数值
	 */
	int getValue();

	/**
	 * 展示文案。
	 *
	 * @return 中文说明
	 */
	String getLabel();
}
