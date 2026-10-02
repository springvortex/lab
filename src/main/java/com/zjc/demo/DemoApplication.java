package com.zjc.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用启动入口。
 * <p>
 * <b>派生新项目时注意：</b>改包名（如 {@code com.yourco.yourapp}）后，本类必须放在新包的根位置，
 * 否则子包下的 Bean 不会被扫描到；同时建议把类名改为 {@code XxxApplication}，与
 * {@code spring.application.name} 保持一致，便于日志与监控识别。
 *
 * @author jiancai.zhong
 */
@SpringBootApplication
public class DemoApplication {

    /**
     * 启动 Spring 容器并拉起内嵌 Web 服务器，本方法常驻不返回。
     *
     * <p>
     * 命令行参数优先级高于所有配置文件，可用于临时覆盖任意配置项。
     *
     * @param args 命令行参数，例如 {@code --spring.profiles.active=prod}
     */
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

}
