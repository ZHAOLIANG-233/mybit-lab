package com.lab.util;

import com.lab.common.util.SensitiveMasker;
import org.junit.Assert;
import org.junit.Test;

/**
 * 敏感数据掩码工具测试。
 *
 * <p>SQL 日志会把绑定参数原样打印，员工手机号与邮箱因此会落到控制台与日志文件。
 * 掩码工具是最后一道兜底，它本身必须被测试守住——
 * 一个"看起来遮住了"但能还原出号段的实现，比不遮更危险。</p>
 */
public class SensitiveDataMaskingTest {

    /** 用例 1：手机号掩码保留前 3 位与后 2 位，中间位数一一对应 */
    @Test
    public void t1_phoneMaskKeepsOnlyHeadAndTail() {
        String phone = "13800000001";
        String masked = SensitiveMasker.maskPhone(phone);

        Assert.assertEquals("掩码后长度应与原文一致（11 = 3 + 6 + 2）", phone.length(), masked.length());
        Assert.assertTrue("应以原号段开头", masked.startsWith("138"));
        Assert.assertTrue("应以原尾号结尾", masked.endsWith("01"));
        Assert.assertEquals("138******01", masked);
        Assert.assertNotEquals("掩码结果不能等于原文", phone, masked);
        Assert.assertFalse("中间号段不应残留", masked.contains("00000"));
    }

    /** 用例 2：邮箱掩码保留首字符与域名，本地部分其余字符全部遮蔽 */
    @Test
    public void t2_emailMaskKeepsFirstCharAndDomain() {
        String email = "zhangwei@lab.com";
        String masked = SensitiveMasker.maskEmail(email);

        Assert.assertEquals("z*******@lab.com", masked);
        Assert.assertTrue("域名应保留，便于识别来源", masked.endsWith("@lab.com"));
        Assert.assertEquals("长度应一致", email.length(), masked.length());
        Assert.assertFalse("本地部分其余字符不应残留", masked.contains("hangwei"));
    }

    /** 用例 3：null 与空值原样返回，过短的串整体遮蔽（不能越界） */
    @Test
    public void t3_nullAndShortValuesAreHandledSafely() {
        Assert.assertNull(SensitiveMasker.maskPhone(null));
        Assert.assertNull(SensitiveMasker.maskEmail(null));
        Assert.assertEquals("", SensitiveMasker.maskPhone(""));
        Assert.assertEquals("", SensitiveMasker.maskEmail(""));

        Assert.assertEquals("*****", SensitiveMasker.maskPhone("12345"));
        Assert.assertEquals("***", SensitiveMasker.maskPhone("138"));

        Assert.assertEquals("*", SensitiveMasker.maskEmail("a"));
        // 不含 @ 的串无法保留域名，必须整体遮蔽而不是原样返回
        Assert.assertEquals("**********", SensitiveMasker.maskEmail("notanemail"));
    }

    /** 用例 4：通用掩码在参数非法时退化为整体遮蔽，且长度不变 */
    @Test
    public void t4_genericMaskIsConservative() {
        Assert.assertEquals("138******01", SensitiveMasker.mask("13800000001", 3, 2));
        // 保留位数超过原文长度时整体遮蔽，不返回比原文更长的串
        Assert.assertEquals("***", SensitiveMasker.mask("138", 5, 5));
        // 负数参数视为非法，同样整体遮蔽
        Assert.assertEquals("*****", SensitiveMasker.mask("abcde", -1, 2));
        Assert.assertEquals("*****", SensitiveMasker.mask("abcde", 2, -1));
        Assert.assertNull(SensitiveMasker.mask(null, 1, 1));
        Assert.assertEquals("", SensitiveMasker.mask("", 1, 1));
    }
}
