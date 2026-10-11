package com.lab.common.config;

import com.lab.common.exception.BusinessException;
import com.lab.common.exception.ErrorCode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 数据源配置解析器：把连接信息从代码里彻底赶出去。
 *
 * <p><b>优先级</b>（从高到低）：</p>
 * <ol>
 *   <li>{@code -Ddb.xxx=...} 系统属性（临时覆盖最方便，且不会落盘）</li>
 *   <li>{@code DB_XXX} 环境变量（持续集成/部署的推荐方式）</li>
 *   <li>classpath 上的配置文件：优先 {@code db.properties}，其次 {@code jdbc.properties}
 *       （两者都应被 .gitignore 排除，严禁入库）</li>
 *   <li>代码内的非敏感默认值（驱动类、URL、用户名）</li>
 * </ol>
 *
 * <p><b>口令没有默认值</b>：前三级都取不到时直接抛
 * {@link ErrorCode#CONFIG_INVALID} 异常，而不是"悄悄用空口令连库"。
 * 空口令连库的失败现象往往是"Access denied"，很容易被误判成账号问题，
 * 在源码层面直接拒绝反而更快定位。</p>
 */
public final class DbConfig {

    /** 主配置文件名（本地开发使用，已被 .gitignore 排除） */
    private static final String PRIMARY_FILE = "db.properties";

    /** 备用配置文件名：测试资源中复用它，避免同一份连接信息维护两处 */
    private static final String FALLBACK_FILE = "jdbc.properties";

    private static final String DEFAULT_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/ssm_emp"
                    + "?useSSL=false&allowPublicKeyRetrieval=true"
                    + "&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
                    + "&useUnicode=true&rewriteBatchedStatements=true";
    private static final String DEFAULT_USERNAME = "root";

    private final String driver;
    private final String url;
    private final String username;
    private final String password;

    private DbConfig(String driver, String url, String username, String password) {
        this.driver = driver;
        this.url = url;
        this.username = username;
        this.password = password;
    }

    /**
     * 按四级优先级装配配置。
     *
     * @return 已完成解析的配置对象
     * @throws BusinessException 口令缺失（{@link ErrorCode#CONFIG_INVALID}）
     */
    public static DbConfig load() {
        Properties file = loadOptionalFile();
        String driver = resolve("db.driver", "DB_DRIVER", DEFAULT_DRIVER, file);
        String url = resolve("db.url", "DB_URL", DEFAULT_URL, file);
        String username = resolve("db.username", "DB_USERNAME", DEFAULT_USERNAME, file);
        String password = resolveSecret(file);
        return new DbConfig(driver, url, username, password);
    }

    /** 读取可选的配置文件；两个都不存在属于正常情况，返回空 Properties */
    private static Properties loadOptionalFile() {
        Properties props = new Properties();
        for (String name : new String[]{PRIMARY_FILE, FALLBACK_FILE}) {
            if (mergeFromClasspath(props, name) && !props.isEmpty()) {
                break;
            }
        }
        return props;
    }

    /** 从 classpath 读取并合并；读不到返回 false（不视为错误） */
    private static boolean mergeFromClasspath(Properties props, String name) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = DbConfig.class.getClassLoader();
        }
        try (InputStream in = loader.getResourceAsStream(name)) {
            if (in == null) {
                return false;
            }
            props.load(in);
            return true;
        } catch (IOException e) {
            // 可选文件损坏也不直接崩：下一级（默认值/快速失败）会接管
            return false;
        }
    }

    /** 非敏感项的三级回退；都取不到时用给定的默认值 */
    private static String resolve(String systemKey, String envKey, String defaultValue, Properties file) {
        String value = System.getProperty(systemKey);
        if (isNotBlank(value)) {
            return value.trim();
        }
        value = System.getenv(envKey);
        if (isNotBlank(value)) {
            return value.trim();
        }
        value = file.getProperty(systemKey);
        if (isNotBlank(value)) {
            return value.trim();
        }
        return defaultValue;
    }

    /**
     * 口令：系统属性 &gt; 环境变量 &gt; 配置文件，均无则快速失败。
     *
     * <p>与其它项不同，这里刻意"不给默认值也不给空串兜底"。</p>
     */
    private static String resolveSecret(Properties file) {
        String value = System.getProperty("db.password");
        if (value != null) {
            return value;
        }
        value = System.getenv("DB_PASSWORD");
        if (value != null) {
            return value;
        }
        value = file.getProperty("db.password");
        if (value != null) {
            return value;
        }
        throw new BusinessException(ErrorCode.CONFIG_INVALID,
                "数据库口令未配置。请用 -Ddb.password=xxx 或环境变量 DB_PASSWORD=xxx 传入，"
                        + "或复制 db.properties.example 为 db.properties（该文件已被 .gitignore 排除）");
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public String getDriver() {
        return driver;
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    /** 只回显非敏感项，避免口令被日志/测试输出带出去 */
    @Override
    public String toString() {
        return "DbConfig{driver='" + driver + "', url='" + url
                + "', username='" + username + "', password=***}";
    }
}
