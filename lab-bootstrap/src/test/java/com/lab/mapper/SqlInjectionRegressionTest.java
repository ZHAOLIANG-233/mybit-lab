package com.lab.mapper;

import com.lab.common.dto.EmpQuery;
import com.lab.common.entity.Emp;
import com.lab.support.TestBase;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

/**
 * SQL 注入回归测试。
 *
 * <p>用几个注入串验证：用户输入只能走 {@code #{}} 预编译。
 * 若哪天有人为了"方便"把模糊查询改成 {@code ${}} 拼接，
 * 本组用例会立刻失败，而不是等某次安全事故才发现。</p>
 *
 * <p>为什么注入串会真的返回全表：把 {@code ' OR '1'='1} 拼进 SQL 后条件变成
 * {@code WHERE emp_name LIKE '%' OR '1'='1%'}，恒真，于是绕过条件。</p>
 */
public class SqlInjectionRegressionTest extends TestBase {

    /** 用例 1：经典注入串在预编译下只会被当成普通文本，匹配不到任何行 */
    @Test
    public void t1_classicOrInjectionMatchesNothing() {
        EmpQuery q = new EmpQuery();
        q.setEmpName("' OR '1'='1");
        List<Emp> list = mapper.selectByCondition(q);
        Assert.assertTrue("注入串不应匹配到任何员工：" + list.size() + " 行", list.isEmpty());
    }

    /** 用例 2：注释截断与 UNION 注入同样无效 */
    @Test
    public void t2_commentAndUnionPayloadsAreInert() {
        String[] payloads = {
                "'--",
                "'#",
                "' OR 1=1 -- ",
                "x' UNION SELECT emp_id, emp_no, emp_name FROM emp -- ",
                "'; DROP TABLE emp; -- "
        };
        for (String payload : payloads) {
            EmpQuery q = new EmpQuery();
            q.setEmpName(payload);
            List<Emp> list = mapper.selectByCondition(q);
            Assert.assertTrue("载荷应无效果：" + payload + " -> " + list.size() + " 行",
                    list.isEmpty());
        }
        // 表仍然健在，且行数未被破坏
        Assert.assertEquals(16, mapper.selectAll().size());
    }

    /** 用例 3：数值与排序等"没法预编译"的位置全部走白名单，不接收原始输入 */
    @Test
    public void t3_nonPreparablePositionsUseWhitelist() {
        EmpQuery q = new EmpQuery();
        // 直接塞恶意排序列名
        q.setOrderBy("emp_id; DROP TABLE emp");
        Assert.assertEquals("非法列名必须回落到 emp_id", "emp_id", q.getSafeOrderBy());

        q.setOrderBy("(SELECT 1)");
        Assert.assertEquals("表达式同样必须回落", "emp_id", q.getSafeOrderBy());

        q.setOrderBy(null);
        Assert.assertEquals("null 也要有确定行为", "emp_id", q.getSafeOrderBy());

        // 白名单内的合法值正常生效
        q.setOrderBy("salary");
        Assert.assertEquals("salary", q.getSafeOrderBy());

        // 执行一次确认 SQL 依然合法
        List<Emp> list = mapper.selectByCondition(q);
        Assert.assertEquals(15, list.size());
    }

    /** 用例 4：参数里的引号不会破坏 SQL 结构（语法错误也是注入的一种征兆） */
    @Test
    public void t4_quotesInParameterDoNotBreakSql() {
        EmpQuery q = new EmpQuery();
        q.setEmpName("O'Brien");
        List<Emp> list = mapper.selectByCondition(q);
        Assert.assertTrue("含单引号的姓名不应造成语法错误，也不应匹配到人", list.isEmpty());

        q.setEmpName("张'伟");
        Assert.assertTrue(mapper.selectByCondition(q).isEmpty());
    }

    /** 用例 5：数值字段的类型是 BigDecimal，字符串载荷在进入 SQL 之前就被类型系统挡住 */
    @Test
    public void t5_payloadsInNumericFieldsAreRejectedByType() {
        try {
            mapper.selectBySalaryRange(new BigDecimal("0"), new BigDecimal("1 UNION SELECT 1"));
            // 上面的构造会先抛异常，因此这一行正常情况下不可达
            Assert.fail("非法数值应抛 NumberFormatException");
        } catch (NumberFormatException expected) {
            Assert.assertNotNull(expected.getMessage());
        }

        // 换一条路径再验证一次：即便载荷能构造出数值，也不会改变条件的"与"语义
        EmpQuery q = new EmpQuery();
        q.setMinSalary(new BigDecimal("999999"));
        q.setEmpName("' OR '1'='1");
        Assert.assertTrue("两个条件应同时生效，结果为空",
                mapper.selectByCondition(q).isEmpty());
    }

    /** 用例 6：MyBatis-Plus 轨的 Wrapper 同样预编译，不会被字符串拼接绕过 */
    @Test
    public void t6_plusWrapperIsAlsoPrecompiled() {
        try (org.apache.ibatis.session.SqlSession plusSession =
                     com.lab.plus.config.PlusSessionFactory.openSession()) {
            com.lab.plus.mapper.EmpPlusMapper plusMapper =
                    plusSession.getMapper(com.lab.plus.mapper.EmpPlusMapper.class);

            List<Emp> viaLike = plusMapper.selectList(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<Emp>lambdaQuery()
                            .like(Emp::getEmpName, "' OR '1'='1"));
            Assert.assertTrue("MP 轨的 like 也应无效果", viaLike.isEmpty());

            List<Emp> all = plusMapper.selectList(null);
            Assert.assertEquals("全量仍为 15 行（逻辑删除已过滤）", 15, all.size());
        }
    }
}
