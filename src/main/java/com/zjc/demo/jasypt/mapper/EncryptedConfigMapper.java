package com.zjc.demo.jasypt.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjc.demo.jasypt.entity.EncryptedConfig;

/**
 * 加密配置项 Mapper，对应 {@code demo.encrypted_config} 表。
 *
 * <p>
 * 用于验证「同一工程下多个模块的 Mapper 能否被一次扫描全部注册」。
 *
 * @author jiancai.zhong
 */
@Mapper
public interface EncryptedConfigMapper extends BaseMapper<EncryptedConfig> {

	/**
	 * 按配置键前缀查询未删除的配置项，按键名升序。
	 *
	 * <p>
	 * 走 XML 自定义 SQL，验证 {@code namespace} 绑定与本模块 XML 是否被加载。
	 *
	 * @param keyPrefix 配置键前缀，如 {@code db.}
	 * @return 匹配的配置项列表，可能为空
	 */
	List<EncryptedConfig> selectByKeyPrefix(@Param("keyPrefix") String keyPrefix);

}
