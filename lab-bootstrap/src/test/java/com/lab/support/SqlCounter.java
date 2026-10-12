package com.lab.support;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.sql.Connection;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SQL 计数拦截器（仅测试使用）。
 *
 * <p>用来把"N+1 到底发了几条 SQL"从主观感受变成可断言的数字。</p>
 *
 * <p><b>拦截点的选择有讲究</b>（报告第五节问题 2）：</p>
 * <ul>
 *   <li>拦 {@code Executor#query} 统计不到：{@code DefaultSqlSession} 调用 4 参重载，
 *       内部转 6 参属于对象内部调用，不经过代理；嵌套查询由
 *       {@code DefaultResultSetHandler} 直接持有 Executor 执行，同样绕开代理。</li>
 *   <li>拦 {@code StatementHandler#prepare} 可以精确统计条数：每次 SQL 执行都会经
 *       {@code Configuration#newStatementHandler} 新建 handler，并被全局拦截器链包装，
 *       因此主查询与嵌套子查询都会被计入。</li>
 * </ul>
 *
 * <p><b>注册方式</b>：只在 {@code @BeforeClass} 注册一次，测试之间用
 * {@link #reset()} 清零，而不是"用完就移出 Configuration"——
 * {@code Configuration#getInterceptors()} 返回不可变列表，调用 remove 会抛
 * {@code UnsupportedOperationException}。</p>
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare",
                args = {Connection.class, Integer.class})
})
public class SqlCounter implements Interceptor {

    /** 累计计数：一次 SQL 执行必定经过一次 prepare */
    private static final AtomicLong COUNT = new AtomicLong();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        COUNT.incrementAndGet();
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    /** 当前累计条数 */
    public static long count() {
        return COUNT.get();
    }

    /** 清零：每个用例开始前调用，避免上一个用例的计数污染断言 */
    public static void reset() {
        COUNT.set(0L);
    }

    /**
     * 从被代理的 StatementHandler 中读出即将执行的 SQL 文本。
     *
     * <p>取不到时返回 {@code <unknown>}，绝不因为"记录日志"而掩盖真实异常。</p>
     */
    public static String sqlText(Invocation invocation) {
        try {
            Object target = invocation.getTarget();
            MetaObject metaObject = SystemMetaObject.forObject(target);
            Object boundSqlValue = metaObject.getValue("delegate.boundSql");
            BoundSql boundSql = boundSqlValue instanceof BoundSql
                    ? (BoundSql) boundSqlValue
                    : (BoundSql) metaObject.getValue("boundSql");
            String sql = boundSql.getSql();
            return sql == null ? "<unknown>" : sql.replaceAll("\\s+", " ").trim();
        } catch (RuntimeException e) {
            return "<unknown>";
        }
    }
}
