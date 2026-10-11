package com.lab.mybatis.config;

import org.apache.ibatis.session.SqlSession;

import java.util.function.Supplier;

/**
 * 会话模板：把"开关、提交、回滚、关闭"收敛到唯一一处。
 *
 * <p><b>它解决的真实缺陷</b>：原生 MyBatis 下每次写操作都要手写完整的会话生命周期，
 * 一旦漏写 {@code commit()}，MyBatis 会在 {@code close()} 时静默回滚——
 * 程序返回成功、数据库里却没有数据。这种失败不抛异常，只能靠复核查出。</p>
 *
 * <p>把事务边界集中在这里之后，业务方法只表达"做什么"，
 * 且异常路径上的回滚不再依赖开发者的记性。</p>
 */
public class MyBatisSessionTemplate {

    /** 会话来源：默认用工厂新开，也允许注入自定义来源以便测试替身 */
    private final Supplier<SqlSession> sessionSupplier;

    public MyBatisSessionTemplate() {
        this(MyBatisSessionFactory::openSession);
    }

    public MyBatisSessionTemplate(Supplier<SqlSession> sessionSupplier) {
        this.sessionSupplier = sessionSupplier;
    }

    /**
     * 在托管会话中执行回调：正常返回则提交，抛异常则回滚，无论何种情况都关闭。
     *
     * <p>回滚分支刻意捕获 {@code RuntimeException | Error} 而不是 {@code Exception}：
     * 业务异常（RuntimeException）要回滚；{@code Error} 同样需要回滚后再抛出，
     * 否则连接会带着未结束的事务回到池里。</p>
     *
     * @param callback 业务回调
     * @param <T>      返回类型
     * @return 业务结果
     */
    public <T> T withSession(SqlSessionCallback<T> callback) {
        SqlSession session = sessionSupplier.get();
        try {
            T result = callback.apply(session);
            session.commit();
            return result;
        } catch (RuntimeException | Error e) {
            session.rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    /**
     * 只读会话：不做提交，但仍然保证关闭。
     *
     * <p>查询共用同一个入口可以减少"忘记 close 导致连接池耗尽"的概率。</p>
     */
    public <T> T withReadOnlySession(SqlSessionCallback<T> callback) {
        SqlSession session = sessionSupplier.get();
        try {
            return callback.apply(session);
        } finally {
            session.close();
        }
    }
}
