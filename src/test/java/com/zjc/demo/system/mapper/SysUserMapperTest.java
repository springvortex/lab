package com.zjc.demo.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjc.demo.common.constant.system.UserStatus;
import com.zjc.demo.system.entity.SysUser;
import jakarta.annotation.Resource;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link SysUserMapper} 集成测试，直连 {@code application-db.yaml} 指向的库跑真实 SQL
 * （本机没有 Docker，Testcontainers 走不通）。
 *
 * <p>
 * 用例数据统一用 {@code it-sys-user-} 前缀隔离，每个用例前后做物理清理，不依赖也不污染库里已有的数据。
 *
 * @author jiancai.zhong
 */
@SpringBootTest
@DisplayName("SysUserMapper 集成（直连远程库）")
class SysUserMapperTest {

    private static final String USERNAME_PREFIX = "it-sys-user-";

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("插入 + 回读：雪花 ID、驼峰映射、审计字段自动填充")
    void insertAndSelectBack() {
        SysUser user = newUser(USERNAME_PREFIX + "roundtrip");

        assertThat(sysUserMapper.insert(user)).isEqualTo(1);
        assertThat(user.getId()).isNotNull();

        SysUser loaded = sysUserMapper.selectById(user.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getUsername()).isEqualTo(user.getUsername());
        assertThat(loaded.getLastLoginIp()).isEqualTo("127.0.0.1");
        assertThat(loaded.getCreateTime()).isNotNull();
        assertThat(loaded.getUpdateTime()).isNotNull();
        assertThat(loaded.getDeleted()).isZero();
    }

    @Test
    @DisplayName("逻辑删除：deleteById 后查不到，行还在库")
    void logicDelete() {
        SysUser user = newUser(USERNAME_PREFIX + "logicdelete");
        sysUserMapper.insert(user);

        assertThat(sysUserMapper.deleteById(user.getId())).isEqualTo(1);
        assertThat(sysUserMapper.selectById(user.getId())).isNull();

        Integer deleted = jdbc.queryForObject(
                "SELECT deleted FROM sys_user WHERE id = ?", Integer.class, user.getId());
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    @DisplayName("分页：分页插件生效，total 只算命中条件的数据")
    void pagination() {
        sysUserMapper.insert(newUser(USERNAME_PREFIX + "page-a"));
        sysUserMapper.insert(newUser(USERNAME_PREFIX + "page-b"));

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .like(SysUser::getUsername, USERNAME_PREFIX)
                .orderByAsc(SysUser::getUsername);
        Page<SysUser> page = sysUserMapper.selectPage(new Page<>(1, 1), wrapper);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().getFirst().getUsername()).isEqualTo(USERNAME_PREFIX + "page-a");
    }

    private SysUser newUser(String username) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword("it-pwd");
        user.setNickname("集成测试");
        user.setGender(1);
        user.setStatus(UserStatus.ENABLED.getValue());
        user.setLastLoginIp("127.0.0.1");
        return user;
    }

    /** 物理删除，逻辑删除的残留行不占唯一索引但仍要清掉 */
    private void cleanUp() {
        jdbc.update("DELETE FROM sys_user WHERE username LIKE ?", USERNAME_PREFIX + "%");
    }
}
