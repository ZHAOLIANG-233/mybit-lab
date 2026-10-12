package com.lab.support;

import com.lab.mybatis.mapper.EmpMapper;
import com.lab.mybatis.config.MyBatisSessionFactory;
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
 * 测试基类：负责"会话隔离"与"数据基线零污染"。
 *
 * <p>三个约定：</p>
 * <ol>
 *   <li>每个测试方法新开一个 {@code autocommit=false} 的会话，方法结束统一 {@code rollback()}。
 *       任何写操作都会在方法结束时不落库，多个用例之间互不干扰，也不需要按顺序执行。</li>
 *   <li>基类的 JDBC 基线快照<b>刻意绕开 MyBatis</b>，直接用 {@link DriverManager} 查询。
 *       如果基线校验本身走了被测框架，"验证"就变成了自证。</li>
 *   <li>基线的具体数字由 {@link #snapshot()} 在运行期读出来，而不是在断言里写死 16：
 *       这样即便将来调整种子数据，测试依然表达的是"用例没有污染基线"这一真正意图。</li>
 * </ol>
 */
public abstract class TestBase {

    /** 供子类使用的会话；由 {@link #openSession()} 创建 */
    protected SqlSession session;

    /** 常用 Mapper 快捷方式，避免每个用例都写一遍 getMapper */
    protected EmpMapper mapper;

    /** 基线快照：整个测试类加载时取一次 */
    private static Baseline baseline;

    @BeforeClass
    public static void captureBaseline() {
        baseline = snapshot();
        Assert.assertTrue("种子数据未就绪：emp 表为空，请先执行 sql/01_schema.sql 与 sql/02_seed.sql",
                baseline.totalRows > 0);
    }

    @Before
    public void openSession() {
        purgeLeftoverTestRows();
        session = MyBatisSessionFactory.openSession();
        mapper = session.getMapper(EmpMapper.class);
    }

    /**
     * 清理可能残留的测试数据。
     *
     * <p>部分用例为了验证提交/回滚行为必须真的写库，一旦中途断言失败，
     * 清理语句可能没执行，残留行会让后续依赖固定行数的用例连锁失败。
     * 按测试专用工号前缀兜底清理，保证整个测试集可重复执行。</p>
     */
    private static void purgeLeftoverTestRows() {
        try (SqlSession cleanup = MyBatisSessionFactory.openSession()) {
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

    /**
     * 回滚并关闭会话。
     *
     * <p>顺序很重要：必须先 rollback 再 close。虽然 close 也会回滚未提交事务，
     * 但显式回滚能让"用例留下了未提交的修改"这件事在日志里可见。</p>
     */
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

    /**
     * 断言数据库基线与测试类加载时完全一致。
     *
     * <p>它捕捉的是"测试自己把库写脏了"这类问题——例如 finally 里清理失败、
     * 或某次提交忘记回滚。这类污染往往在下一个测试类才以"唯一键冲突"的形式爆发，
     * 归因非常困难，因此这里主动守住。</p>
     */
    protected void assertBaselineUnchanged() {
        Baseline now = snapshot();
        Assert.assertEquals("emp 物理行数被污染", baseline.totalRows, now.totalRows);
        Assert.assertEquals("emp 逻辑删除行数被污染", baseline.deletedRows, now.deletedRows);
        Assert.assertEquals("emp 在职行数被污染", baseline.onJobRows, now.onJobRows);
        Assert.assertEquals("emp 最大主键被污染（说明插入被提交了）", baseline.maxId, now.maxId);
    }

    /** 直接 JDBC 取基线快照 */
    private static Baseline snapshot() {
        Properties props = loadJdbcProperties();
        String url = System.getProperty("db.url", props.getProperty("db.url"));
        String user = System.getProperty("db.username", props.getProperty("db.username"));
        String password = System.getProperty("db.password", props.getProperty("db.password"));

        String sql = "SELECT COUNT(*) AS total, "
                + "SUM(CASE WHEN del_flag = 1 THEN 1 ELSE 0 END) AS deleted, "
                + "SUM(CASE WHEN status = 1 AND del_flag = 0 THEN 1 ELSE 0 END) AS onJob, "
                + "MAX(emp_id) AS maxId FROM emp";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            Assert.assertTrue("基线查询未返回结果", rs.next());
            return new Baseline(rs.getInt("total"), rs.getInt("deleted"),
                    rs.getInt("onJob"), rs.getInt("maxId"));
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "无法连接数据库，请确认 lab MySQL 已启动（bash scripts/start-lab-mysql.sh）", e);
        }
    }

    private static Properties loadJdbcProperties() {
        Properties props = new Properties();
        try (InputStream in = TestBase.class.getClassLoader().getResourceAsStream("jdbc.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            // 读不到就完全依赖 -D 参数；不因为可选文件缺失直接失败
        }
        return props;
    }

    /** 基线数据快照 */
    private static final class Baseline {
        private final int totalRows;
        private final int deletedRows;
        private final int onJobRows;
        private final int maxId;

        private Baseline(int totalRows, int deletedRows, int onJobRows, int maxId) {
            this.totalRows = totalRows;
            this.deletedRows = deletedRows;
            this.onJobRows = onJobRows;
            this.maxId = maxId;
        }
    }
}
