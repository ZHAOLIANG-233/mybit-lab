package com.lab.plus.config;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * MyBatis-Plus 轨的 SqlSessionFactory 持有者。
 *
 * <p>与原生轨的 {@code MyBatisSessionFactory} 结构对称：
 * 工厂单例只建一次，会话每次新开。
 * 两条轨各自持有工厂，互不影响，也便于在测试里分别断言。</p>
 */
public final class PlusSessionFactory {

    private static final SqlSessionFactory FACTORY = PlusConfigurationFactory.build();

    private PlusSessionFactory() {
        throw new UnsupportedOperationException("工厂持有者不允许实例化");
    }

    public static SqlSessionFactory getFactory() {
        return FACTORY;
    }

    /**
     * 开启不自动提交的会话。
     *
     * <p>同样刻意关闭 autocommit：逻辑删除、乐观锁这些行为需要显式提交，
     * 测试也必须能通过 rollback 保证数据基线零污染。</p>
     */
    public static SqlSession openSession() {
        return FACTORY.openSession(false);
    }
}
