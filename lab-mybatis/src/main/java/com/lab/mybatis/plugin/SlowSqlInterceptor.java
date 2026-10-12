package com.lab.mybatis.plugin;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 慢 SQL 拦截器：统计语句耗时，超过阈值就打印告警。
 *
 * <p><b>拦截点的选择是这类插件的关键</b>（报告第五节问题 1 就是这个问题的排查过程）：</p>
 * <ul>
 *   <li>{@code Executor#query} 拦不到嵌套查询：{@code DefaultSqlSession} 调用的是 4 参重载，
 *       内部又走 6 参重载，属于对象内部调用不经过代理；嵌套查询更是由
 *       {@code DefaultResultSetHandler} 直接持有 Executor 执行，同样绕开代理。</li>
 *   <li>{@code StatementHandler#prepare} 只在"准备语句"阶段，拿不到真实执行耗时，
 *       但它<b>每次 SQL 执行都会经过</b>，因此适合用来"数 SQL 条数"。</li>
 *   <li>{@code StatementHandler#query / update} 才是真正执行的地方，
 *       统计耗时必须拦这里。</li>
 * </ul>
 *
 * <p>阈值支持四级回退：{@code -DslowSql.thresholdMs} &gt; 环境变量
 * {@code SLOW_SQL_THRESHOLD_MS} &gt; mybatis-config.xml 中 {@code <property>}
 * &gt; 内置默认 200ms。</p>
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "query",
                args = {Statement.class, org.apache.ibatis.session.ResultHandler.class}),
        @Signature(type = StatementHandler.class, method = "update",
                args = {Statement.class})
})
public class SlowSqlInterceptor implements Interceptor {

    /** MyBatis 统一日志接口：注意它<b>没有</b> SLF4J 的 {} 占位符重载，只能用字符串拼接 */
    private static final Log LOG = LogFactory.getLog(SlowSqlInterceptor.class);

    /** 系统属性名 */
    public static final String SYSTEM_PROPERTY = "slowSql.thresholdMs";
    /** 环境变量名 */
    public static final String ENV_KEY = "SLOW_SQL_THRESHOLD_MS";
    /** 默认阈值（毫秒） */
    public static final long DEFAULT_THRESHOLD_MS = 200L;

    /**
     * 累计命中次数（含未超阈值的语句）。
     *
     * <p>做成静态计数器是为了让测试能在"不连数据库"的前提下断言拦截器是否真的生效；
     * 生产代码不应依赖这个值，故只暴露读方法。</p>
     */
    private static final AtomicLong HIT_COUNT = new AtomicLong();

    /** 当前生效阈值，默认取默认值；setProperties 可能覆盖它 */
    private Long thresholdMs = DEFAULT_THRESHOLD_MS;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startNanos = System.nanoTime();
        HIT_COUNT.incrementAndGet();
        try {
            return invocation.proceed();
        } finally {
            // 放在 finally：即使 SQL 抛异常，也要记录"这条语句花了多久才失败"
            long costMs = (System.nanoTime() - startNanos) / 1_000_000L;
            if (costMs >= resolveThreshold()) {
                // 取 SQL 文本本身做容错：取不到也不能让"打日志"掩盖真正的 SQL 异常
                LOG.warn("[SLOW-SQL] " + costMs + " ms | " + safeSql(invocation));
            }
        }
    }

    /**
     * 读取配置。
     *
     * <p>这里刻意<b>不</b>用传入的 property 覆盖系统属性/环境变量：
     * 外部参数（-D / 环境变量）应当拥有更高优先级，
     * 否则"临时用 -DslowSql.thresholdMs=0 打开全量告警"会被配置文件里的 200 静默吃掉。</p>
     */
    @Override
    public void setProperties(Properties properties) {
        long fromProperty = parse(properties == null ? null : properties.getProperty("thresholdMs"));
        // 只有在外部两级都没给出阈值时，配置文件的 property 才生效
        if (parse(System.getProperty(SYSTEM_PROPERTY)) < 0
                && parse(System.getenv(ENV_KEY)) < 0) {
            this.thresholdMs = fromProperty >= 0 ? fromProperty : DEFAULT_THRESHOLD_MS;
        }
    }

    /** 运行时解析生效阈值，保证 -D / 环境变量的改动无需重新装配 */
    private long resolveThreshold() {
        long fromSystem = parse(System.getProperty(SYSTEM_PROPERTY));
        if (fromSystem >= 0) {
            return fromSystem;
        }
        long fromEnv = parse(System.getenv(ENV_KEY));
        if (fromEnv >= 0) {
            return fromEnv;
        }
        return thresholdMs == null ? DEFAULT_THRESHOLD_MS : thresholdMs;
    }

    private static long parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return -1L;
        }
        try {
            long value = Long.parseLong(raw.trim());
            // 负数阈值没有意义，直接视为"未配置"
            return value < 0 ? -1L : value;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /** 尽力取出 SQL 文本；任何失败都退化为 &lt;unknown sql&gt;，绝不向外抛异常 */
    private static String safeSql(Invocation invocation) {
        try {
            Object target = invocation.getTarget();
            MetaObject metaObject = SystemMetaObject.forObject(target);
            Object boundSqlValue = metaObject.getValue("delegate.boundSql");
            BoundSql boundSql = boundSqlValue instanceof BoundSql
                    ? (BoundSql) boundSqlValue
                    : (BoundSql) metaObject.getValue("boundSql");
            String sql = boundSql.getSql();
            return sql == null ? "<unknown sql>" : sql.replaceAll("\\s+", " ").trim();
        } catch (RuntimeException e) {
            return "<unknown sql>";
        }
    }

    /** 本次 JVM 内拦截命中次数（只读，测试用） */
    public static long hitCount() {
        return HIT_COUNT.get();
    }

    /** 清零计数：测试之间互相隔离，避免"上一个用例的命中数"污染断言 */
    public static void resetHitCount() {
        HIT_COUNT.set(0L);
    }

    /** 当前生效阈值，便于测试断言"外部参数优先级最高" */
    public long currentThresholdMs() {
        return resolveThreshold();
    }

    /**
     * 标准插件包装：返回代理对象，把本拦截器的签名方法织入目标。
     */
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    /**
     * 提供"注销"能力，且<b>不会</b>触碰 {@code Configuration#getInterceptors()}。
     *
     * <p>背景（报告第五节问题 2 的第一层原因）：{@code Configuration#getInterceptors()}
     * 返回 {@code Collections.unmodifiableList(...)}，直接调用 {@code remove}
     * 会抛 {@code UnsupportedOperationException}。正确做法是拿到拦截器链
     * （{@code PluginWrapper} 内部持有的 {@code InterceptorChain}），
     * 从它自己的列表中摘除。</p>
     *
     * <p>生产代码不应使用本方法；它只为测试提供"用完即卸"的隔离能力。</p>
     *
     * @param target      被插件包装过的目标对象
     * @param interceptor 之前注册的拦截器实例
     * @return true 表示确实移除了一个实例
     */
    public static boolean unwrapFrom(Object target, Interceptor interceptor) {
        if (!java.lang.reflect.Proxy.isProxyClass(target.getClass())) {
            return false;
        }
        java.lang.reflect.InvocationHandler handler = java.lang.reflect.Proxy.getInvocationHandler(target);
        if (!(handler instanceof Plugin)) {
            return false;
        }
        try {
            java.lang.reflect.Field interceptorField = Plugin.class.getDeclaredField("interceptor");
            interceptorField.setAccessible(true);
            Object inner = interceptorField.get(handler);
            if (!(inner instanceof org.apache.ibatis.plugin.InterceptorChain)) {
                return false;
            }
            java.lang.reflect.Field listField =
                    org.apache.ibatis.plugin.InterceptorChain.class.getDeclaredField("interceptors");
            listField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<Interceptor> interceptors = (java.util.List<Interceptor>) listField.get(inner);
            return interceptors.remove(interceptor);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
