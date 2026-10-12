package com.lab.config;

import com.lab.common.entity.Emp;
import com.lab.common.exception.BusinessException;
import com.lab.mybatis.config.MyBatisSessionFactory;
import com.lab.mybatis.config.MyBatisSessionTemplate;
import com.lab.mybatis.mapper.EmpMapper;
import org.apache.ibatis.session.SqlSession;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 会话模板与事务边界测试。
 *
 * <p>本组用例专门验证"会话生命周期收口"这一设计：
 * 提交、回滚、关闭都由模板负责，业务代码不再有机会漏写。</p>
 */
public class MyBatisSessionTemplateTest {

    /** 用例 1：回调正常返回时自动提交，且用【新会话】能复核到数据 */
    @Test
    public void t1_commitsOnSuccess() {
        MyBatisSessionTemplate template = new MyBatisSessionTemplate();
        String empNo = "E9001A";
        Integer newId = template.withSession(session -> {
            Emp emp = Emp.of(empNo, "事务提交验证", 1, "研发部", new BigDecimal("10000.00"));
            session.getMapper(EmpMapper.class).insert(emp);
            return emp.getEmpId();
        });

        Assert.assertNotNull("插入后应回填主键", newId);
        try {
            // 关键点：必须用"另一个会话"复核。同会话内查得到不能证明已提交
            try (SqlSession verify = MyBatisSessionFactory.openSession()) {
                Emp loaded = verify.getMapper(EmpMapper.class).selectById(newId);
                Assert.assertNotNull("新会话查不到数据，说明事务未提交", loaded);
                Assert.assertEquals(empNo, loaded.getEmpNo());
            }
        } finally {
            // 本用例必须真的写库（否则无法验证提交），所以显式清理，保证基线不被污染
            try (SqlSession cleanup = MyBatisSessionFactory.openSession()) {
                cleanup.getMapper(EmpMapper.class).deleteById(newId);
                cleanup.commit();
            }
        }
    }

    /** 用例 2：回调抛异常时自动回滚，数据不落库 */
    @Test
    public void t2_rollsBackOnFailure() {
        MyBatisSessionTemplate template = new MyBatisSessionTemplate();
        String empNo = "E9002B";
        final Integer[] capturedId = new Integer[1];

        try {
            template.withSession(session -> {
                Emp emp = Emp.of(empNo, "事务回滚验证", 1, "研发部", new BigDecimal("11000.00"));
                session.getMapper(EmpMapper.class).insert(emp);
                capturedId[0] = emp.getEmpId();
                // 模拟业务中途失败
                throw new BusinessException(com.lab.common.exception.ErrorCode.BUSINESS_REJECTED, "故意失败");
            });
            Assert.fail("应向外抛出异常");
        } catch (BusinessException expected) {
            Assert.assertTrue(expected.getMessage().contains("故意失败"));
        }

        Assert.assertNotNull("回滚前应已拿到主键", capturedId[0]);
        try (SqlSession verify = MyBatisSessionFactory.openSession()) {
            Assert.assertNull("抛出异常后数据不应落库",
                    verify.getMapper(EmpMapper.class).selectById(capturedId[0]));
        }
    }

    /** 用例 3：只读会话不提交但一定关闭；同时验证"漏写 commit 会静默回滚"这一真实缺陷 */
    @Test
    public void t3_readOnlySessionKeepsDataUncommitted() {
        MyBatisSessionTemplate template = new MyBatisSessionTemplate();
        String empNo = "E9003C";

        // 只读入口写数据：模板不会 commit，关闭会话时 MyBatis 会回滚
        Integer newId = template.withReadOnlySession(session -> {
            Emp emp = Emp.of(empNo, "未提交验证", 1, "研发部", new BigDecimal("12000.00"));
            session.getMapper(EmpMapper.class).insert(emp);
            return emp.getEmpId();
        });

        try (SqlSession verify = MyBatisSessionFactory.openSession()) {
            Assert.assertNull("只读会话不应提交任何写入，这正是\"漏写 commit\"的现象",
                    verify.getMapper(EmpMapper.class).selectById(newId));
        }
    }

    /** 用例 4：模板在异常路径上也不会把连接泄漏出去（连续失败后仍能正常开会话） */
    @Test
    public void t4_doesNotLeakConnectionOnRepeatedFailure() {
        MyBatisSessionTemplate template = new MyBatisSessionTemplate();
        for (int i = 0; i < 20; i++) {
            try {
                template.withSession(session -> {
                    throw new IllegalStateException("第 " + 1 + " 次故意失败");
                });
            } catch (IllegalStateException ignored) {
                // 预期异常，忽略
            }
        }
        // 若连接池被耗尽，下面这行会超时或抛异常
        try (SqlSession session = MyBatisSessionFactory.openSession()) {
            Assert.assertNotNull(session.getConnection());
        } catch (Exception e) {
            Assert.fail("连续 20 次异常后连接池不可用，说明会话未正确关闭：" + e.getMessage());
        }
    }

    /** 用例 5：模板内可以直接使用 Mapper，且日期/金额等类型往返不失真 */
    @Test
    public void t5_typeRoundTripIsLossless() {
        MyBatisSessionTemplate template = new MyBatisSessionTemplate();
        LocalDate hire = LocalDate.of(2023, 6, 15);
        BigDecimal salary = new BigDecimal("18888.88");

        try (SqlSession readSession = MyBatisSessionFactory.openSession()) {
            Emp sample = readSession.getMapper(EmpMapper.class).selectById(1);
            Assert.assertNotNull(sample);
            Assert.assertEquals("LocalDate 映射应精确到日",
                    LocalDate.of(2015, 7, 1), sample.getHireDate());
            Assert.assertEquals("DECIMAL 映射到 BigDecimal 不应丢精度",
                    0, new BigDecimal("32000.00").compareTo(sample.getSalary()));
        }

        // 再验证一次写入-读回的往返
        Integer id = template.withSession(session -> {
            Emp emp = Emp.of("E9005E", "类型往返", 2, "测试部", salary);
            emp.setHireDate(hire);
            session.getMapper(EmpMapper.class).insert(emp);
            return emp.getEmpId();
        });
        try {
            try (SqlSession verify = MyBatisSessionFactory.openSession()) {
                Emp loaded = verify.getMapper(EmpMapper.class).selectById(id);
                Assert.assertEquals(hire, loaded.getHireDate());
                Assert.assertEquals(0, salary.compareTo(loaded.getSalary()));
            }
        } finally {
            try (SqlSession cleanup = MyBatisSessionFactory.openSession()) {
                cleanup.getMapper(EmpMapper.class).deleteById(id);
                cleanup.commit();
            }
        }
    }
}
