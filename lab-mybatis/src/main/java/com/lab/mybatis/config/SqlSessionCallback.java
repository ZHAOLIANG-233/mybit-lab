package com.lab.mybatis.config;

import org.apache.ibatis.session.SqlSession;

/**
 * 会话回调：把"用会话做什么"抽取为函数式接口。
 *
 * <p>配合 {@link MyBatisSessionTemplate#withSession(SqlSessionCallback)} 使用，
 * 业务代码只需要写 {@code session -> session.getMapper(XxxMapper.class).xxx()}，
 * 不再关心开关、提交、回滚、关闭。</p>
 *
 * @param <T> 回调的返回类型
 */
@FunctionalInterface
public interface SqlSessionCallback<T> {

    /**
     * 在一次会话内完成业务操作。
     *
     * @param session 由模板创建并托管的会话，回调内不要自行 close
     * @return 业务结果
     * @throws RuntimeException 抛出任何运行时异常都会触发模板回滚
     */
    T apply(SqlSession session);
}
