package com.lab.config;

import com.lab.mybatis.config.MyBatisSessionFactory;
import com.lab.mybatis.plugin.SlowSqlInterceptor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Collections;
import java.util.Properties;

/**
 * 慢 SQL 拦截器测试。
 *
 * <p>本组用例<b>完全不连数据库</b>：用 JDK 动态代理构造 {@code StatementHandler} 替身，
 * 用 {@code interceptor.plugin(stub)} 手动完成织入，再调用
 * {@code prepare / query / update} 观察命中与计数 —— 这正是 MyBatis
 * {@code InterceptorChain#pluginAll} 在运行期做的事。</p>
 *
 * <p>这样"插件是否真的生效"可以被独立验证，不会因为数据库不可用而无法排查插件问题。</p>
 */
public class SlowSqlInterceptorTest {

    /** 本类自己持有的实例，用于验证 setProperties 的优先级行为 */
    private static SlowSqlInterceptor interceptor;

    /** 注册时（来自 mybatis-config.xml）的阈值，用于在每个用例后复原 */
    private static long registeredThreshold;

    private String savedSystemProperty;

    @BeforeClass
    public static void registerInterceptor() {
        Configuration configuration = MyBatisSessionFactory.getFactory().getConfiguration();
        interceptor = (SlowSqlInterceptor) configuration.getInterceptors().stream()
                .filter(i -> i instanceof SlowSqlInterceptor)
                .findFirst().orElseThrow(() -> new IllegalStateException("慢 SQL 插件未注册"));
        System.clearProperty(SlowSqlInterceptor.SYSTEM_PROPERTY);
        registeredThreshold = interceptor.currentThresholdMs();
    }

    @AfterClass
    public static void restore() {
        System.clearProperty(SlowSqlInterceptor.SYSTEM_PROPERTY);
    }

    @Before
    public void clearExternalConfig() {
        // 清空外部参数并复原阈值，保证每个用例从"配置默认值"出发
        savedSystemProperty = System.getProperty(SlowSqlInterceptor.SYSTEM_PROPERTY);
        System.clearProperty(SlowSqlInterceptor.SYSTEM_PROPERTY);
        Properties restore = new Properties();
        restore.setProperty("thresholdMs", String.valueOf(registeredThreshold));
        interceptor.setProperties(restore);
        SlowSqlInterceptor.resetHitCount();
    }

    @After
    public void restoreSystemProperty() {
        if (savedSystemProperty != null) {
            System.setProperty(SlowSqlInterceptor.SYSTEM_PROPERTY, savedSystemProperty);
        } else {
            System.clearProperty(SlowSqlInterceptor.SYSTEM_PROPERTY);
        }
    }

    /** 用例 1：未配置外部参数时，阈值回落到 mybatis-config.xml 中的 200ms */
    @Test
    public void t1_defaultThresholdComesFromConfigFile() {
        // 用一个全新的实例模拟"刚被 configuration 解析完"的状态
        SlowSqlInterceptor fresh = new SlowSqlInterceptor();
        Properties props = new Properties();
        props.setProperty("thresholdMs", "150");
        fresh.setProperties(props);
        Assert.assertEquals("配置文件里的值应生效", 150L, fresh.currentThresholdMs());

        // 已注册的实例来自 mybatis-config.xml，其 property 为 200
        Assert.assertEquals("配置文件中声明的是 200ms", 200L, interceptor.currentThresholdMs());
    }

    /** 用例 2：-D 系统属性的优先级高于配置文件（防止"临时调阈值被静默吃掉"） */
    @Test
    public void t2_systemPropertyOverridesConfigFile() {
        Properties props = new Properties();
        props.setProperty("thresholdMs", "150");
        interceptor.setProperties(props);
        Assert.assertEquals(150L, interceptor.currentThresholdMs());

        System.setProperty(SlowSqlInterceptor.SYSTEM_PROPERTY, "0");
        Assert.assertEquals("-D 参数必须压过配置文件", 0L, interceptor.currentThresholdMs());

        // 再调一次 setProperties 也不能把 -D 覆盖掉
        interceptor.setProperties(props);
        Assert.assertEquals("-D 参数仍然有效", 0L, interceptor.currentThresholdMs());
    }

    /** 用例 3：非法阈值文本被安全忽略，回落而不是抛异常 */
    @Test
    public void t3_invalidThresholdFallsBackSafely() {
        System.setProperty(SlowSqlInterceptor.SYSTEM_PROPERTY, "not-a-number");
        Assert.assertEquals("非法值应回落为配置文件/默认值",
                200L, interceptor.currentThresholdMs());

        System.setProperty(SlowSqlInterceptor.SYSTEM_PROPERTY, "-5");
        Assert.assertEquals("负数阈值视为未配置", 200L, interceptor.currentThresholdMs());

        System.setProperty(SlowSqlInterceptor.SYSTEM_PROPERTY, " 42 ");
        Assert.assertEquals("应容忍首尾空格", 42L, interceptor.currentThresholdMs());
    }

    /**
     * 用例 4：签名方法命中并计数。
     *
     * <p>当前 {@code @Signature} 声明的正是 {@code query} 与 {@code update} 两个方法
     * ——耗时统计必须拦真实执行点，{@code prepare} 只负责"准备语句"。
     * 早期我误以为 {@code prepare} 也在签名里，断言立刻失败；
     * 这个用例现在把这个事实固定下来：<b>声明了什么，才拦截什么</b>。</p>
     */
    @Test
    public void t4_signedMethodsAreInterceptedAndCounted() throws Throwable {
        SlowSqlInterceptor.resetHitCount();
        Object stub = wrap(stubHandler("SELECT emp_id, emp_name FROM emp WHERE emp_id = ?"));

        Assert.assertEquals(0L, SlowSqlInterceptor.hitCount());
        invoke(stub, "query", new Class<?>[]{Statement.class, ResultHandler.class},
                new Object[]{null, null});
        Assert.assertEquals("query 应命中", 1L, SlowSqlInterceptor.hitCount());

        invoke(stub, "update", new Class<?>[]{Statement.class}, new Object[]{null});
        Assert.assertEquals("update 应命中", 2L, SlowSqlInterceptor.hitCount());
    }

    /** 用例 5：未在 @Signature 中声明的方法不计数，证明命中判定精准 */
    @Test
    public void t5_unsignedMethodsAreNotIntercepted() throws Throwable {
        SlowSqlInterceptor.resetHitCount();
        Object stub = wrap(stubHandler("UPDATE emp SET salary = ? WHERE emp_id = ?"));

        invoke(stub, "getBoundSql", new Class<?>[]{}, new Object[]{});
        invoke(stub, "getParameterHandler", new Class<?>[]{}, new Object[]{});
        // prepare 不在签名里：它只是"准备语句"，统计耗时不该拦它
        invoke(stub, "prepare", new Class<?>[]{Connection.class, Integer.class},
                new Object[]{null, 10});

        Assert.assertEquals("未声明的方法不应被拦截", 0L, SlowSqlInterceptor.hitCount());

        // 反证：声明过的方法一定会被拦截
        invoke(stub, "update", new Class<?>[]{Statement.class}, new Object[]{null});
        Assert.assertEquals(1L, SlowSqlInterceptor.hitCount());
    }

    /** 用例 6：异常路径也会计数，且异常原样抛出（不能把 SQL 异常吞掉） */
    @Test
    public void t6_exceptionStillCountsAndPropagates() {
        SlowSqlInterceptor.resetHitCount();
        Object failing = wrap(failingHandler("UPDATE emp SET salary = ? WHERE emp_id = ?"));
        try {
            invoke(failing, "update", new Class<?>[]{Statement.class}, new Object[]{null});
            Assert.fail("底层异常应向外抛出");
        } catch (Throwable expected) {
            Assert.assertEquals("SQLException", expected.getClass().getSimpleName());
            Assert.assertEquals("异常路径同样要计数", 1L, SlowSqlInterceptor.hitCount());
        }
    }

    /** 用例 7：阈值 0 时每条语句都告警，用计数验证"全量告警模式"可用 */
    @Test
    public void t7_zeroThresholdEnablesWarnForEveryStatement() throws Throwable {
        System.setProperty(SlowSqlInterceptor.SYSTEM_PROPERTY, "0");
        Assert.assertEquals(0L, interceptor.currentThresholdMs());

        SlowSqlInterceptor.resetHitCount();
        Object stub = wrap(stubHandler("SELECT COUNT(*) FROM emp"));
        for (int i = 0; i < 7; i++) {
            invoke(stub, "query", new Class<?>[]{Statement.class, ResultHandler.class},
                    new Object[]{null, null});
        }
        Assert.assertEquals("7 次执行应命中 7 次", 7L, SlowSqlInterceptor.hitCount());
    }

    /**
     * 手动完成织入：这一行等价于 MyBatis 运行期
     * {@code InterceptorChain#pluginAll} 对 StatementHandler 做的事。
     *
     * <p><b>只能织入一次</b>。早期我在构造替身时就调用了一次 {@code Plugin.wrap}，
     * 测试里又包了一层，结果拦截器落在内层：外层 PluginWrapper 的签名表匹配不到方法，
     * 直接 {@code method.invoke} 透传，计数恒为 0 —— 一个非常像"插件没生效"的假象。
     * 现在替身保持裸的 StatementHandler，织入只在这里发生。</p>
     */
    private static Object wrap(StatementHandler rawTarget) {
        return interceptor.plugin(rawTarget);
    }

    /** 构造 StatementHandler 替身：正常返回 */
    private static StatementHandler stubHandler(String sql) {
        return (StatementHandler) Proxy.newProxyInstance(
                SlowSqlInterceptorTest.class.getClassLoader(),
                new Class<?>[]{StatementHandler.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "prepare":
                            // 返回传入的 Statement 即可，签名匹配后拦截器会 proceed
                            return args == null ? null : args[0];
                        case "query":
                            return Collections.emptyList();
                        case "update":
                            return 1;
                        case "getBoundSql":
                            return new BoundSql(new Configuration(), sql,
                                    Collections.emptyList(), null);
                        default:
                            return null;
                    }
                });
    }

    /** 构造 StatementHandler 替身：update/query 时抛 SQLException，验证异常路径 */
    private static StatementHandler failingHandler(String sql) {
        return (StatementHandler) Proxy.newProxyInstance(
                SlowSqlInterceptorTest.class.getClassLoader(),
                new Class<?>[]{StatementHandler.class},
                (proxy, method, args) -> {
                    if ("update".equals(method.getName())) {
                        throw new java.sql.SQLException("模拟语句执行失败");
                    }
                    if ("getBoundSql".equals(method.getName())) {
                        return new BoundSql(new Configuration(), sql,
                                Collections.emptyList(), null);
                    }
                    return null;
                });
    }

    /** 通过反射调用代理上的方法，method 的类型必须与 @Signature 声明一致才能命中 */
    private static Object invoke(Object target, String methodName,
                                 Class<?>[] parameterTypes, Object[] args) throws Throwable {
        try {
            return target.getClass().getMethod(methodName, parameterTypes).invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
