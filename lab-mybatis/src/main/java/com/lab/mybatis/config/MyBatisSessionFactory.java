package com.lab.mybatis.config;

import com.lab.common.config.DbConfig;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * SqlSessionFactory 的手工装配点（全程不使用 Spring）。
 *
 * <p>两个关键点：</p>
 * <ol>
 *   <li><b>全局配置与凭据分离</b>：{@code mybatis-config.xml} 里只写
 *       {@code ${db.xxx}} 占位符，真实值由 {@link DbConfig} 解析后以
 *       {@code Properties} 形式在构建时传入。这样配置文件可以安全入库。</li>
 *   <li><b>工厂是应用级单例</b>：SqlSessionFactory 构建代价高且线程安全，
 *       在类加载时创建一次；而 SqlSession 是方法级的、非线程安全的，
 *       每次使用都要新开，因此这里只暴露 {@code openSession()}。</li>
 * </ol>
 */
public final class MyBatisSessionFactory {

    private static final String CONFIG_LOCATION = "mybatis-config.xml";

    /** 应用级单例；volatile + 类初始化保证多线程下的可见性 */
    private static final SqlSessionFactory FACTORY = build();

    private MyBatisSessionFactory() {
        throw new UnsupportedOperationException("工厂持有者不允许实例化");
    }

    private static SqlSessionFactory build() {
        try (InputStream in = Resources.getResourceAsStream(CONFIG_LOCATION)) {
            Properties properties = new Properties();
            DbConfig db = DbConfig.load();
            properties.setProperty("db.driver", db.getDriver());
            properties.setProperty("db.url", db.getUrl());
            properties.setProperty("db.username", db.getUsername());
            properties.setProperty("db.password", db.getPassword());
            return new SqlSessionFactoryBuilder().build(in, properties);
        } catch (IOException e) {
            throw new IllegalStateException("读取 MyBatis 配置文件失败：" + CONFIG_LOCATION, e);
        }
    }

    /**
     * 获取全局工厂（只读）。
     *
     * @return 单例 SqlSessionFactory
     */
    public static SqlSessionFactory getFactory() {
        return FACTORY;
    }

    /**
     * 开启一个<b>不自动提交</b>的会话。
     *
     * <p>刻意不开放 autocommit=true：实验里要验证"漏写 commit 会导致数据回滚"，
     * 就必须让事务边界显式可见。测试基类也正是依赖这一点做统一回滚。</p>
     *
     * @return 新会话，使用方负责 close
     */
    public static SqlSession openSession() {
        return FACTORY.openSession(false);
    }
}
