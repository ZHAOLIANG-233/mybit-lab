package com.lab.config;

import com.lab.mybatis.config.MyBatisSessionFactory;
import org.apache.ibatis.session.SqlSession;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * 连通性冒烟测试（环境自检第一关）。
 *
 * <p>不依赖任何业务数据：只回答三个问题——
 * 数据源能不能连上、JDBC 元数据是否与预期一致、会话能不能正常开关。
 * 这三项一旦失败，后面所有用例的报错都会变成噪声。</p>
 */
public class ConnectionSmokeTest {

    /** 用例 1：通过 MyBatis 的 POOLED 数据源拿到连接，并核对产品名与版本 */
    @Test
    public void t1_dataSourceConnectsAndMetaMatches() throws Exception {
        try (SqlSession session = MyBatisSessionFactory.openSession()) {
            Connection conn = session.getConnection();
            Assert.assertNotNull("连接不应为 null", conn);
            Assert.assertFalse("连接不应处于已关闭状态", conn.isClosed());

            DatabaseMetaData meta = conn.getMetaData();
            String product = meta.getDatabaseProductName();
            Assert.assertEquals("数据库产品应为 MySQL", "MySQL", product);
            Assert.assertTrue("驱动应报告为 MySQL Connector/J",
                    meta.getDriverName().toLowerCase().contains("mysql"));
            System.out.println("[SMOKE] product=" + product
                    + " version=" + meta.getDatabaseProductVersion()
                    + " driver=" + meta.getDriverName() + " " + meta.getDriverVersion());
        }
    }

    /** 用例 2：会话可以正常开启与关闭，且关闭后不可再用 */
    @Test
    public void t2_sessionLifecycleWorks() {
        SqlSession session = MyBatisSessionFactory.openSession();
        Assert.assertNotNull(session);
        Connection conn = session.getConnection();
        Assert.assertNotNull(conn);
        session.close();
        // 关闭后再取连接应抛异常，这是"生命周期真的结束了"的判据
        try {
            session.getConnection();
            Assert.fail("会话关闭后仍能取到连接，说明 close 未生效");
        } catch (RuntimeException expected) {
            Assert.assertNotNull(expected.getMessage());
        }
    }

    /** 用例 3：数据库与连接串中的库名一致（防止连到别的库上做实验） */
    @Test
    public void t3_connectedSchemaIsSsmEmp() throws Exception {
        try (SqlSession session = MyBatisSessionFactory.openSession()) {
            Connection conn = session.getConnection();
            String catalog = conn.getCatalog();
            Assert.assertEquals("应连接到 ssm_emp 库", "ssm_emp", catalog);
        }
    }
}
