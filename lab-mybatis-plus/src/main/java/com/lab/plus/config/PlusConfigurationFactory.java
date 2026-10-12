package com.lab.plus.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.lab.common.config.DbConfig;
import com.lab.plus.mapper.EmpPlusMapper;
import org.apache.ibatis.logging.log4j.Log4jImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.datasource.pooled.PooledDataSource;

/**
 * MyBatis-Plus 运行环境的手工装配点（不用任何 starter）。
 *
 * <p><b>最容易踩的一个坑就在这里</b>（报告第五节问题与附录第 9 条）：
 * 必须用 {@code MybatisConfiguration} 而不是原生 {@code org.apache.ibatis.session.Configuration}，
 * 并用 {@code MybatisSqlSessionFactoryBuilder} 构建。
 * 否则 {@code BaseMapper} 的通用 CRUD 语句不会被注入，
 * 调用时抛 {@code BindingException: Invalid bound statement (not found)}，
 * 而接口继承、实体注解看起来都是"对的"，排查方向极易被带偏。</p>
 *
 * <p>不连数据库就能自证的办法：{@code configuration.hasStatement("...EmpPlusMapper.selectById")}。</p>
 */
public final class PlusConfigurationFactory {

    /** 环境 id，与数据源绑定 */
    private static final String ENVIRONMENT_ID = "plus-development";

    private PlusConfigurationFactory() {
        throw new UnsupportedOperationException("工厂类不允许实例化");
    }

    /**
     * 构建 MyBatis-Plus 配置。
     *
     * @return 已注册数据源、拦截器与 Mapper 的配置对象
     */
    public static MybatisConfiguration buildConfiguration() {
        DbConfig db = DbConfig.load();

        // 关键点 1：必须用 MybatisConfiguration，它是 MP 注入通用 CRUD 语句的前提
        MybatisConfiguration configuration = new MybatisConfiguration();

        // 与原生轨保持一致的全局开关：驼峰映射 + Log4j 日志实现
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(Log4jImpl.class);
        configuration.setCallSettersOnNulls(true);

        // 数据源与事务工厂手工装配，全程无 Spring
        configuration.setEnvironment(new Environment(ENVIRONMENT_ID,
                new JdbcTransactionFactory(), new PooledDataSource(
                db.getDriver(), db.getUrl(), db.getUsername(), db.getPassword())));

        // 关键点 2：分页与乐观锁都靠拦截器实现，不注册就"静默失效"
        configuration.addInterceptor(mybatisPlusInterceptor());

        // 注册 Mapper：这一步会触发 BaseMapper 语句注入
        configuration.addMapper(EmpPlusMapper.class);
        return configuration;
    }

    /**
     * MyBatis-Plus 主拦截器。
     *
     * <p><b>顺序要求</b>：分页插件必须放在乐观锁插件之前。
     * 若顺序反了，分页 SQL 会因为先被改写而缺少 LIMIT 或总数恒为 0——
     * 而且不抛任何异常，只能靠断言发现（报告第五节问题 4）。</p>
     */
    public static MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /**
     * 构建 SqlSessionFactory（应用级单例，由 {@link PlusSessionFactory} 持有）。
     */
    public static SqlSessionFactory build() {
        return new MybatisSqlSessionFactoryBuilder().build(buildConfiguration());
    }
}
