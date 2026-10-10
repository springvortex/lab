package com.zjc.demo.core.log;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * {@link LogDesensitizeConverter} 纯单元测试，不起 Spring。
 *
 * @author jiancai.zhong
 */
@DisplayName("日志脱敏转换器")
class LogDesensitizeConverterTest {

    private final LogDesensitizeConverter converter = new LogDesensitizeConverter();

    @Test
    @DisplayName("手机号保留前三后四")
    void maskPhone() {
        assertThat(convert("用户 13812345678 登录成功")).isEqualTo("用户 138****5678 登录成功");
    }

    @Test
    @DisplayName("身份证保留前六后四，末位 X 也支持")
    void maskIdCard() {
        assertThat(convert("证件 440101199001011234 提交")).isEqualTo("证件 440101********1234 提交");
        assertThat(convert("证件 44010119900101123X 提交")).isEqualTo("证件 440101********123X 提交");
    }

    @Test
    @DisplayName("银行卡过 Luhn 才打码：真卡号打码，订单号/雪花 ID 原样")
    void maskBankCardOnlyWhenLuhnPasses() {
        assertThat(convert("卡号 4111111111111111 扣款")).isEqualTo("卡号 4111********1111 扣款");
        assertThat(convert("订单号 1234567890123456 已创建")).isEqualTo("订单号 1234567890123456 已创建");
        assertThat(convert("用户 1912345678901234567 下单")).isEqualTo("用户 1912345678901234567 下单");
    }

    @Test
    @DisplayName("13 位毫秒时间戳不被当成手机号")
    void timestampUntouched() {
        assertThat(convert("耗时 1739232000000 ms")).isEqualTo("耗时 1739232000000 ms");
    }

    @Test
    @DisplayName("一句话里多种敏感数据同时打码")
    void mixedMessage() {
        String out = convert("用户 13812345678 用卡 4111111111111111 缴费，证件 440101199001011234");
        assertThat(out).isEqualTo("用户 138****5678 用卡 4111********1111 缴费，证件 440101********1234");
    }

    @Test
    @DisplayName("空消息原样返回")
    void emptyMessage() {
        assertThat(convert("")).isEmpty();
    }

    private String convert(String message) {
        Logger logger = (Logger) LoggerFactory.getLogger("test");
        LoggingEvent event = new LoggingEvent("fqcn", logger, Level.INFO, message, null, new Object[0]);
        return converter.convert(event);
    }
}
