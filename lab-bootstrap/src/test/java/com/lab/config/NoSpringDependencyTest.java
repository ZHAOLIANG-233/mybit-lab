package com.lab.config;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 检查工程确实没有引入 Spring。
 *
 * <p>实验要求全程纯 Java + MyBatis，不使用 Spring / Spring Boot。</p>
 *
 * <p>MyBatis-Plus 的 {@code mybatis-spring} 相关依赖是 {@code <optional>true</optional>}，
 * 正常情况下不会传递引入；但"正常情况"会随着依赖调整而变化。
 * 与其在文档里写一句承诺，不如把承诺写成断言：一旦将来有人引入了 starter，
 * 这个测试会立刻失败。</p>
 */
public class NoSpringDependencyTest {

    /** 用例 1：运行期 classpath 上不存在 Spring 核心类，且两个框架入口类都在 */
    @Test
    public void t1_noSpringOnRuntimeClasspath() {
        Assert.assertFalse("检测到 Spring 核心类，违反\"禁用 Spring\"约束",
                classPresent("org.springframework.context.ApplicationContext"));
        Assert.assertFalse("检测到 Spring Boot，违反\"禁用 Spring\"约束",
                classPresent("org.springframework.boot.SpringApplication"));
        Assert.assertFalse("检测到 mybatis-spring 集成包，违反手工装配约定",
                classPresent("org.mybatis.spring.SqlSessionFactoryBean"));

        // 反向确认：确实加载的是纯 MyBatis 与 MyBatis-Plus
        Assert.assertTrue("原生 MyBatis 入口类缺失",
                classPresent("org.apache.ibatis.session.SqlSessionFactoryBuilder"));
        Assert.assertTrue("MyBatis-Plus 入口类缺失",
                classPresent("com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder"));

        System.out.println("[NO-SPRING] Spring 相关类均不存在；MyBatis 与 MyBatis-Plus 入口正常");
    }

    /** 用例 2：类路径上没有任何 spring-*.jar，防止"没加载到但已引入"的隐性依赖 */
    @Test
    public void t2_classpathContainsNoSpringArchives() {
        // Java 9+ 的应用类加载器不再是 URLClassLoader，因此改用 java.class.path 枚举
        String classpath = System.getProperty("java.class.path", "");
        Assert.assertFalse("无法读取 java.class.path，检查环境不完整", classpath.isEmpty());

        List<String> springJars = new ArrayList<>();
        for (String entry : classpath.split(java.io.File.pathSeparator)) {
            String lower = entry.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".jar") && lower.contains("spring")) {
                springJars.add(new File(entry).getName());
            }
        }
        Assert.assertTrue("classpath 上出现 Spring 制品：" + springJars, springJars.isEmpty());
        System.out.println("[NO-SPRING] classpath 条目数=" + classpath.split(java.io.File.pathSeparator).length
                + "，其中 Spring 制品=0");
    }

    private static boolean classPresent(String className) {
        try {
            Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
