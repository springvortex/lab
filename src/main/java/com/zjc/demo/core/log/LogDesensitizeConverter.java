package com.zjc.demo.core.log;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志脱敏转换器，在 logback-spring.xml 里注册为 {@code %desensitize} 替换 {@code %msg} 使用。
 *
 * <p>
 * 只兜底自由文本：身份证（保留前六后四）、银行卡（16 位且过 Luhn 校验才打码，防止误伤雪花 ID、
 * 订单号）、手机号（保留前三后四）。JSON 里的 password 等无格式字段由 {@code WebLogAspect}
 * 按字段名打码，不在这里处理；异常堆栈（%ex）也不经过本转换器。
 *
 * <p>
 * 长模式必须先跑：手机号 11 位的数字段会出现在身份证里，顺序错了会把证件号打烂。
 *
 * @author jiancai.zhong
 */
public class LogDesensitizeConverter extends MessageConverter {

    /** 身份证 18 位（末位可为 X），前后断言保证不命中更长数字串的中间段 */
    private static final Pattern IDCARD_PATTERN =
            Pattern.compile("(?<!\\d)(\\d{6})\\d{8}(\\d{3}[\\dXx])(?!\\d)");

    /** 银行卡按 16 位整段识别，是否真卡号交给 Luhn 校验；雪花 ID 19 位、时间戳 13 位，天然不误伤 */
    private static final Pattern BANK_PATTERN = Pattern.compile("(?<!\\d)(\\d{16})(?!\\d)");

    /** 手机号 11 位，同样带边界断言 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)");

    @Override
    public String convert(ILoggingEvent event) {
        String msg = super.convert(event);
        if (msg == null || msg.isEmpty()) {
            return msg;
        }
        msg = maskByPattern(msg, IDCARD_PATTERN, 8);
        msg = maskBankCard(msg);
        msg = PHONE_PATTERN.matcher(msg).replaceAll("$1****$2");
        return msg;
    }

    /**
     * 按正则打码：保留 group(1)、group(2)，中间替换为指定数量的星号。
     *
     * @param msg         原文
     * @param pattern     长模式正则，恰好两个捕获组
     * @param maskedCount 中间打码的星号数量
     * @return 打码后的文本
     */
    private String maskByPattern(String msg, Pattern pattern, int maskedCount) {
        Matcher matcher = pattern.matcher(msg);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String replacement = matcher.group(1) + "*".repeat(maskedCount) + matcher.group(2);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 16 位数字段过 Luhn 校验后按「前四后四」打码，校验不过原样保留。
     *
     * @param msg 原文
     * @return 打码后的文本
     */
    private String maskBankCard(String msg) {
        Matcher matcher = BANK_PATTERN.matcher(msg);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String digits = matcher.group(1);
            String replacement = passesLuhn(digits)
                    ? digits.substring(0, 4) + "*".repeat(8) + digits.substring(12)
                    : digits;
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Luhn 校验（模 10 算法），银行卡号必然通过。
     *
     * @param digits 纯数字串
     * @return 校验通过返回 {@code true}
     */
    private boolean passesLuhn(String digits) {
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }
}
