package com.lab.common.util;

/**
 * 敏感数据掩码工具。
 *
 * <p>SQL 日志会把绑定参数原样打印出来，员工手机号、邮箱因此会落到控制台与日志文件里。
 * 本工具用于"展示层"的脱敏，规则刻意保守：宁可多遮几位，也不要漏出一段可还原的号段。</p>
 *
 * <p>注意：这不是加密，只是显示层遮蔽。真正的合规要求是"敏感字段不入库日志"，
 * 掩码只是最后一道兜底。</p>
 */
public final class SensitiveMasker {

    /** 掩码字符 */
    private static final String MASK = "*";

    private SensitiveMasker() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 手机号掩码：保留前 3 位与后 2 位。
     *
     * <p>例：{@code 13800000001} -&gt; {@code 138*******01}</p>
     *
     * @param phone 原始手机号，允许为 null
     * @return 掩码后的字符串；null 原样返回，过短的串整体掩码
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }
        if (phone.length() <= 5) {
            return repeat(MASK, phone.length());
        }
        int keepHead = 3;
        int keepTail = 2;
        return phone.substring(0, keepHead)
                + repeat(MASK, phone.length() - keepHead - keepTail)
                + phone.substring(phone.length() - keepTail);
    }

    /**
     * 邮箱掩码：保留首字符与域名，其余遮蔽。
     *
     * <p>例：{@code zhangwei@lab.com} -&gt; {@code z*******@lab.com}</p>
     *
     * @param email 原始邮箱，允许为 null
     * @return 掩码后的字符串；不含 @ 的串整体掩码
     */
    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return repeat(MASK, email.length());
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() == 1) {
            return local + MASK + domain;
        }
        return local.charAt(0) + repeat(MASK, local.length() - 1) + domain;
    }

    /**
     * 通用掩码：长度不足时退化为整体遮蔽，绝不返回比原文更长的串。
     *
     * @param raw       原始值
     * @param keepHead  保留头部字符数
     * @param keepTail  保留尾部字符数
     */
    public static String mask(String raw, int keepHead, int keepTail) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        if (keepHead < 0 || keepTail < 0 || raw.length() <= keepHead + keepTail) {
            return repeat(MASK, raw.length());
        }
        return raw.substring(0, keepHead)
                + repeat(MASK, raw.length() - keepHead - keepTail)
                + raw.substring(raw.length() - keepTail);
    }

    private static String repeat(String unit, int times) {
        if (times <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(unit.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(unit);
        }
        return sb.toString();
    }
}
