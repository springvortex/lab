package com.zjc.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

/**
 * {@link MybatisPlusConfig} 单元测试：不启动 Spring 容器，直接断言拦截器链的组成与顺序。
 *
 * <p>
 * <b>为什么单独测顺序：</b>分页插件放错位置不会抛异常，只会让 COUNT SQL 统计不准——
 * 表现是「分页结果偶尔对不上」，属于线上最难排查的一类问题。这里把顺序钉成一条会红的测试。
 *
 * @author jiancai.zhong
 */
class MybatisPlusConfigTest {

	/**
	 * 拦截器链必须包含乐观锁与分页两个拦截器，且分页排在最后。
	 */
	@Test
	@DisplayName("拦截器链：乐观锁在前、分页在后")
	void interceptorChainHasOptimisticLockThenPagination() {
		MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();

		assertThat(interceptor.getInterceptors()).hasSize(2);
		assertThat(interceptor.getInterceptors().get(0)).isInstanceOf(OptimisticLockerInnerInterceptor.class);
		assertThat(interceptor.getInterceptors().get(1)).isInstanceOf(PaginationInnerInterceptor.class);
	}

	/**
	 * 分页拦截器必须显式指定 PostgreSQL 方言，并带上单页条数上限。
	 */
	@Test
	@DisplayName("分页拦截器：PostgreSQL 方言 + 单页条数上限")
	void paginationUsesPostgreDialectAndMaxLimit() {
		MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();

		InnerInterceptor last = interceptor.getInterceptors().get(1);
		assertThat(last).isInstanceOf(PaginationInnerInterceptor.class);

		PaginationInnerInterceptor pagination = (PaginationInnerInterceptor) last;
		assertThat(pagination.getDbType()).isEqualTo(DbType.POSTGRE_SQL);
		assertThat(pagination.getMaxLimit()).isEqualTo(MybatisPlusConfig.MAX_PAGE_SIZE);
	}
}
