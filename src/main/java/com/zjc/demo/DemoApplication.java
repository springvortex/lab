package com.zjc.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用启动入口。派生新项目时改包名后，本类必须留在新包的根位置，否则扫不到子包下的 Bean。
 *
 * @author jiancai.zhong
 */
@SpringBootApplication
public class DemoApplication {

    /**
     * 启动 Spring 容器并拉起内嵌 Web 服务器。命令行参数优先级高于配置文件。
     *
     * @param args 命令行参数，如 {@code --spring.profiles.active=prod}
     */
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

}
