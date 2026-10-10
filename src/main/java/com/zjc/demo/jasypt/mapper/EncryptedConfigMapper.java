package com.zjc.demo.jasypt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjc.demo.jasypt.entity.EncryptedConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 加密配置项 Mapper。
 *
 * @author jiancai.zhong
 */
@Mapper
public interface EncryptedConfigMapper extends BaseMapper<EncryptedConfig> {

    /**
     * 按配置键前缀查询未删除的配置项，按键名升序。
     *
     * @param keyPrefix 配置键前缀，如 {@code db.}
     * @return 匹配的配置项列表，可能为空
     */
    List<EncryptedConfig> selectByKeyPrefix(@Param("keyPrefix") String keyPrefix);

}
