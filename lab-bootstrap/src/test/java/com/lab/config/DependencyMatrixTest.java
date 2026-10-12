package com.lab.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.lab.mybatis.config.MyBatisSessionFactory;
import com.lab.mybatis.plugin.SlowSqlInterceptor;
import com.lab.plus.config.PlusConfigurationFactory;
import org.apache.ibatis.session.Configuration;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 依赖矩阵测试：核对"运行期实际加载了哪些组件、哪个版本"。
 *
 * <p>常规做法是看 {@code mvn dependency:tree}，但配置里写了什么不等于运行期加载的是什么。
 * 本测试以<b>运行期真实结果</b>为证据：从类加载器读版本、从 JDBC 元数据读驱动版本、
 * 从 Configuration 读拦截器数量。</p>
 *
 * <p>它守护的是三类"不报错但结果不对"的问题：版本漂移、插件静默失效、Spring 被意外引入。</p>
 */
public class DependencyMatrixTest {

    /** 期望的 MyBatis 版本：必须由父 pom 仲裁得到，而不是 MyBatis-Plus 自带的 3.5.15 */
    private static final String EXPECTED_MYBATIS = "3.5.16";

    /** 用例 1：MyBatis 运行期版本等于父 pom 仲裁值 */
    @Test
    public void t1_mybatisVersionIsArbitratedToExpected() {
        String actual = readVersion("org.apache.ibatis.session.Configuration");
        assertEquals("MyBatis 版本被 MyBatis-Plus 自带的版本覆盖了，"
                + "请检查子模块是否显式声明了 mybatis 的 version", EXPECTED_MYBATIS, actual);
        System.out.println("[DEP] mybatis=" + actual);
    }

    /** 用例 2：MyBatis-Plus 与注解包版本一致 */
    @Test
    public void t2_myBatisPlusVersionsAreConsistent() {
        String core = readVersion("com.baomidou.mybatisplus.core.MybatisConfiguration");
        String annotation = readVersion("com.baomidou.mybatisplus.annotation.TableName");
        assertNotNull(core);
        assertEquals("mybatis-plus 核心与注解包版本必须一致", core, annotation);
        System.out.println("[DEP] mybatis-plus=" + core + " annotation=" + annotation);
    }

    /** 用例 3：MySQL 驱动来自新坐标 com.mysql:mysql-connector-j */
    @Test
    public void t3_mysqlDriverComesFromNewCoordinates() throws Exception {
        String driverClass = readVersion("com.mysql.cj.jdbc.Driver");
        assertNotNull("MySQL 驱动未加载", driverClass);
        try (Connection conn = MyBatisSessionFactory.openSession().getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String driverName = meta.getDriverName();
            Assert.assertTrue("驱动应来自 Connector/J：" + driverName,
                    driverName.toLowerCase().contains("mysql connector"));
            System.out.println("[DEP] driver=" + driverName + " " + meta.getDriverVersion());
        }
    }

    /** 用例 4：原子轨的 Configuration 中只注册了 1 个插件（慢 SQL 拦截器） */
    @Test
    public void t4_mybatisConfigurationHasExactlyOnePlugin() {
        Configuration configuration = MyBatisSessionFactory.getFactory().getConfiguration();
        assertEquals("原生轨应只注册慢 SQL 插件", 1, configuration.getInterceptors().size());
        assertTrue(configuration.getInterceptors().get(0) instanceof SlowSqlInterceptor);
    }

    /** 用例 5：MyBatis-Plus 轨注册了分页 + 乐观锁两个内置拦截器 */
    @Test
    public void t5_plusConfigurationRegistersPaginationAndOptimisticLocker() {
        MybatisConfiguration configuration = PlusConfigurationFactory.buildConfiguration();
        assertEquals("MP 轨应有 1 个外层拦截器", 1, configuration.getInterceptors().size());
        MybatisPlusInterceptor main =
                (MybatisPlusInterceptor) configuration.getInterceptors().get(0);
        List<String> innerNames = new ArrayList<>();
        main.getInterceptors().forEach(i -> innerNames.add(i.getClass().getSimpleName()));
        assertEquals("应注册分页 + 乐观锁两个内置拦截器", 2, innerNames.size());
        assertEquals("分页插件必须排在乐观锁之前",
                0, innerNames.indexOf("PaginationInnerInterceptor"));
        assertEquals(1, innerNames.indexOf("OptimisticLockerInnerInterceptor"));
        System.out.println("[DEP] plus inner interceptors=" + innerNames);
    }

    /**
     * 用例 6：BaseMapper 的通用 CRUD 语句确实被注入。
     *
     * <p>这里是"语句名必须与实现真实对齐"的例子：MyBatis-Plus 3.5.5 注入的是
     * {@code selectById/selectBatchIds/selectList/selectMaps/selectObjs/selectCount}
     * 与 {@code insert/update/updateById/delete/deleteById/deleteBatchIds} 共 12 条。
     * 早期我凭印象把 {@code selectMapsPage} 之类的方法名也列进去了，断言立刻失败——
     * 这正是"以运行期事实为准、而不是以记忆为准"的价值。</p>
     */
    @Test
    public void t6_baseMapperStatementsAreInjected() {
        MybatisConfiguration configuration = PlusConfigurationFactory.buildConfiguration();
        String[] injected = {
                "insert", "delete", "deleteById", "deleteBatchIds",
                "update", "updateById",
                "selectById", "selectBatchIds", "selectList", "selectMaps",
                "selectObjs", "selectCount"
        };
        for (String suffix : injected) {
            String statementId = "com.lab.plus.mapper.EmpPlusMapper." + suffix;
            assertTrue("通用 CRUD 语句未注入：" + statementId,
                    configuration.hasStatement(statementId));
        }

        // 自定义 @Select 方法同样应当注册
        for (String suffix : new String[]{"selectDeptStat", "countOnJobByDept", "countByMinSalary"}) {
            String statementId = "com.lab.plus.mapper.EmpPlusMapper." + suffix;
            assertTrue("自定义查询未注册：" + statementId,
                    configuration.hasStatement(statementId));
        }
        assertEquals("注入的通用语句条数应为 12", 12, injected.length);
    }

    /** 用例 7：MyBatis-Plus 的 SQL 解析器在运行期可加载（分页插件靠它改写 SQL） */
    @Test
    public void t7_jsqlparserIsOnClasspath() {
        try {
            Class<?> clazz = Class.forName("net.sf.jsqlparser.parser.CCJSqlParser");
            assertNotNull("jsqlparser 缺失，分页插件无法改写 SQL", clazz);
        } catch (ClassNotFoundException e) {
            Assert.fail("jsqlparser 不在运行期 classpath 上：" + e.getMessage());
        }
    }

    /** 用例 8：两轨的配置互不污染（原生轨配置里不含 MP 的拦截器类型） */
    @Test
    public void t8_twoTracksDoNotPolluteEachOther() {
        Configuration plain = MyBatisSessionFactory.getFactory().getConfiguration();
        Assert.assertFalse("原生轨不应出现 MybatisPlusInterceptor",
                plain.getInterceptors().get(0) instanceof MybatisPlusInterceptor);

        Configuration plus = PlusConfigurationFactory.buildConfiguration();
        Assert.assertFalse("MP 轨不应出现 SlowSqlInterceptor",
                plus.getInterceptors().get(0) instanceof SlowSqlInterceptor);
    }

    /** 从类所在 JAR 的包实现版本读取版本号；读不到返回 null 而不是抛异常 */
    private static String readVersion(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Package pkg = clazz.getPackage();
            if (pkg == null || pkg.getImplementationVersion() == null) {
                // 部分 IDE/测试运行方式下实现版本为空，退化为读取类所在 JAR 文件名
                String path = clazz.getProtectionDomain().getCodeSource() == null
                        ? null : clazz.getProtectionDomain().getCodeSource().getLocation().getPath();
                if (path == null) {
                    return null;
                }
                java.util.regex.Matcher m =
                        java.util.regex.Pattern.compile("([\\w.\\-]+)-(\\d+\\.\\d+\\.\\d+[\\w.\\-]*)\\.jar")
                                .matcher(path);
                return m.find() ? m.group(2) : null;
            }
            return pkg.getImplementationVersion();
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
