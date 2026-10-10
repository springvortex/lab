package com.zjc.demo.core.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：Mapper 扫描 + 拦截器链。
 *
 * @author jiancai.zhong
 */
@Configuration
@MapperScan(basePackages = "com.zjc.demo.**.mapper", annotationClass = Mapper.class)
public class MybatisPlusConfig {

    /**
     * 单页最大条数，超出会被静默截断。
     */
    public static final long MAX_PAGE_SIZE = 500L;

    /**
     * 拦截器链：乐观锁 + 分页。当前无实体标 {@code @Version}，乐观锁不生效。
     *
     * @return 拦截器链
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 分页放最后，否则 COUNT 可能不准
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }
}
