package com.zjc.demo.core.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * MyBatis-Plus 字段自动填充，维护创建时间与更新时间。
 *
 * <p>
 * 生效要求「实体字段标 {@code @TableField(fill = ...)}」与「本处理器按字段名赋值」<b>成对出现</b>，
 * 少一边都静默不填。
 *
 * @author jiancai.zhong
 */
@Component
public class MybatisPlusMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时填充：两个时间都取当前时刻，保证同一条记录的两个时间完全一致。
     *
     * @param metaObject 当前插入实体的元数据对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    /**
     * 更新时填充：只刷新更新时间。
     *
     * <p>
     * 这里不能用 {@code strictUpdateFill}——它遇到非空值会跳过，而更新时实体是从库里查出来的、
     * {@code updateTime} 必然非空，结果就是「更新时间」永远不变，且不报任何错。
     *
     * @param metaObject 当前更新实体的元数据对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        if (metaObject.hasSetter("updateTime")) {
            metaObject.setValue("updateTime", LocalDateTime.now());
        }
    }
}
