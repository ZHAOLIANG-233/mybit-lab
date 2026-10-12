package com.lab.support;

import com.lab.plus.config.PlusSessionFactory;
import com.lab.plus.mapper.EmpPlusMapper;
import org.apache.ibatis.session.SqlSession;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * MyBatis-Plus 轨的测试基类。
 *
 * <p>与 {@link TestBase} 结构对称，但会话来自 {@link PlusSessionFactory}。
 * 两条轨各自持有独立的 SqlSessionFactory，因此可以分别断言各自的行为，
 * 也能在同一个测试方法里同时打开两个会话做"三轨一致性"比对。</p>
 */
public abstract class PlusTestBase {

    protected SqlSession session;

    protected EmpPlusMapper mapper;

    private static Baseline baseline;

    @BeforeClass
    public static void captureBaseline() {
        baseline = snapshot();
        Assert.assertTrue("种子数据未就绪", baseline.totalRows > 0);
    }

    @Before
    public void openSession() {
        purgeLeftoverTestRows();
        session = PlusSessionFactory.openSession();
        mapper = session.getMapper(EmpPlusMapper.class);
    }

    /**
     * 清理可能残留的测试数据。
     *
     * <p>为什么需要：有些用例必须"真的提交"才能验证逻辑删除、分页等行为，
     * 一旦某次运行中断言失败，后面的清理代码就可能没跑到，残留行会让<b>后续所有</b>
     * 依赖固定行数的用例连锁失败——而且现象是"总数多了 1"，很难一眼归因。</p>
     *
     * <p>这里按测试专用的工号前缀兜底清理，使整个测试集具备"可重复执行"的性质。
     * 原生轨的 {@code TestBase} 也做了同样的事。</p>
     */
    private static void purgeLeftoverTestRows() {
        try (SqlSession cleanup = PlusSessionFactory.openSession()) {
            java.sql.Statement statement = cleanup.getConnection().createStatement();
            try {
                statement.executeUpdate(
                        "DELETE FROM emp WHERE emp_no LIKE 'P7%' OR emp_no LIKE 'E9%'");
                cleanup.commit();
            } finally {
                statement.close();
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("清理残留测试数据失败", e);
        }
    }

    @After
    public void rollbackAndClose() {
        if (session != null) {
            try {
                session.rollback();
            } finally {
                session.close();
            }
        }
    }

    protected void assertBaselineUnchanged() {
        Baseline now = snapshot();
        Assert.assertEquals("emp 物理行数被污染", baseline.totalRows, now.totalRows);
        Assert.assertEquals("emp 逻辑删除行数被污染", baseline.deletedRows, now.deletedRows);
        Assert.assertEquals("emp 最大主键被污染", baseline.maxId, now.maxId);
    }

    private static Baseline snapshot() {
        Properties props = new Properties();
        try (InputStream in = PlusTestBase.class.getClassLoader().getResourceAsStream("jdbc.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            // 忽略：完全依赖 -D 参数
        }
        String url = System.getProperty("db.url", props.getProperty("db.url"));
        String user = System.getProperty("db.username", props.getProperty("db.username"));
        String password = System.getProperty("db.password", props.getProperty("db.password"));

        String sql = "SELECT COUNT(*) AS total, "
                + "SUM(CASE WHEN del_flag = 1 THEN 1 ELSE 0 END) AS deleted, "
                + "MAX(emp_id) AS maxId FROM emp";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            Assert.assertTrue("基线查询未返回结果", rs.next());
            return new Baseline(rs.getInt("total"), rs.getInt("deleted"), rs.getInt("maxId"));
        } catch (SQLException e) {
            throw new IllegalStateException("无法连接数据库，请先启动 lab MySQL", e);
        }
    }

    private static final class Baseline {
        private final int totalRows;
        private final int deletedRows;
        private final int maxId;

        private Baseline(int totalRows, int deletedRows, int maxId) {
            this.totalRows = totalRows;
            this.deletedRows = deletedRows;
            this.maxId = maxId;
        }
    }
}
