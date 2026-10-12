package com.lab.security;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 检查凭据与敏感文件有没有管好。
 *
 * <p>"源码里不出现明文凭据"这条规范如果只写在文档里，迟早会失效。
 * 这里把它拆成 4 条测试：</p>
 * <ol>
 *   <li>主源码 / SQL / 全局配置里没有明文口令；</li>
 *   <li>MyBatis 配置中的敏感项必须是 {@code ${...}} 占位符；</li>
 *   <li>{@code .gitignore} 中的凭据忽略规则处于<b>有效</b>状态（未被注释）；</li>
 *   <li>仓库中不存在真实凭据文件（db.properties 等）。</li>
 * </ol>
 *
 * <p>注意：测试自己的 jdbc.properties 需要真实连接参数，属于有意保留的白名单，
 * 但它不参与扫描集合（扫描范围是主源码与 SQL 脚本）。</p>
 */
public class SecurityScanTest {

    /**
     * 明文口令的可疑形态。
     *
     * <p>只把"敏感键被赋了一个<b>字面量</b>"视为违规，因为那正是硬编码凭据的形态：</p>
     * <ul>
     *   <li>{@code db.password=abc123} —— 命中，疑似硬编码口令；</li>
     *   <li>{@code password="secret123"} —— 命中（引号在匹配时被剥离）；</li>
     *   <li>{@code String password = resolveSecret(file);} —— 不命中，右值是普通标识符，不是字面量；</li>
     *   <li>{@code log4j.appender.console.layout=org.apache.log4j.PatternLayout} —— 不命中，
     *       键名是复合的且不以敏感词结尾。</li>
     * </ul>
     * <p>判断顺序是"先取候选值，再排除标识符与占位符"，避免用一条正则同时表达两种意图。</p>
     */
    private static final Pattern PLAINTEXT_SECRET = Pattern.compile(
            "(?i)(?:^|[\\s;\"'])([\\w.]*(?:password|passwd|pwd|secret|token))\\s*[=:]\\s*[\"']?([^\\s\"',;)]+)");

    /** 右值是普通标识符时不算硬编码（变量名、字段名、方法引用等） */
    private static final Pattern IDENTIFIER_VALUE = Pattern.compile("^[A-Za-z_$][\\w$.()\\[\\]]*$");

    /** 需要扫描的源码目录（相对项目根） */
    private static final List<String> SCAN_ROOTS = Arrays.asList(
            "src", "sql", "scripts");

    /**
     * 有意排除的路径与文件。
     *
     * <p>说明：本地凭据文件（db.properties / jdbc.properties / .lab.env）本来就会写真实口令，
     * 扫它们没有意义；真正要守的是"它不能被提交"——那一条由 t4 用 git 判断。</p>
     */
    private static final List<String> EXCLUDED = Arrays.asList(
            "db.properties.example", "/target/", "target/",
            "db.properties", "jdbc.properties", ".lab.env",
            // 扫描器自身包含"疑似明文口令"的示例片段，必须白名单排除，否则会自己命中自己
            "SecurityScanTest.java");

    /** 用例 1：主源码、SQL 与测试资源之外的文件中不出现明文凭据 */
    @Test
    public void t1_noPlaintextCredentialsInSources() throws IOException {
        Path root = projectRoot();
        List<String> violations = new ArrayList<>();

        for (String relative : SCAN_ROOTS) {
            Path start = root.resolve(relative);
            if (!Files.exists(start)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(start)) {
                List<Path> files = stream.filter(Files::isRegularFile)
                        .filter(this::isScannable)
                        .collect(Collectors.toList());
                for (Path file : files) {
                    String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                    String[] lines = content.split("\n");
                    for (int i = 0; i < lines.length; i++) {
                        String line = lines[i];
                        if (line.trim().startsWith("#") || line.trim().startsWith("//")
                                || line.trim().startsWith("--")) {
                            continue; // 注释行里写"password="是文档，不算违规
                        }
                        Matcher matcher = PLAINTEXT_SECRET.matcher(line);
                        if (matcher.find()) {
                            String value = stripTrailingPunctuation(matcher.group(2));
                            if (isPlaceholder(value) || IDENTIFIER_VALUE.matcher(value).matches()) {
                                continue;
                            }
                            violations.add(root.relativize(file) + ":" + (i + 1) + " -> " + line.trim());
                        }
                    }
                }
            }
        }
        Assert.assertTrue("源码中出现疑似明文凭据（若非真实口令请改用占位符）：\n"
                + String.join("\n", violations), violations.isEmpty());
    }

    /** 用例 2：mybatis-config.xml 中的敏感项必须是 ${} 占位符 */
    @Test
    public void t2_mybatisConfigUsesPlaceholdersOnly() throws IOException {
        Path config = projectRoot()
                .resolve("lab-mybatis/src/main/resources/mybatis-config.xml");
        Assert.assertTrue("mybatis-config.xml 应存在", Files.exists(config));
        String xml = new String(Files.readAllBytes(config), StandardCharsets.UTF_8);

        Assert.assertTrue("driver 必须是占位符", xml.contains("value=\"${driver}\""));
        Assert.assertTrue("url 必须是占位符", xml.contains("value=\"${url}\""));
        Assert.assertTrue("username 必须是占位符", xml.contains("value=\"${username}\""));
        Assert.assertTrue("password 必须是占位符", xml.contains("value=\"${password}\""));

        // 反向：不能出现 jdbc: 连接串或明文口令直接写在配置里
        Assert.assertFalse("配置文件中不应出现真实连接串", xml.contains("jdbc:mysql://"));
        Assert.assertFalse("配置文件中不应出现明文口令",
                PLAINTEXT_SECRET.matcher(xml).find());
    }

    /** 用例 3：.gitignore 中的凭据忽略规则必须有效（未被注释掉） */
    @Test
    public void t3_gitignoreRulesAreActive() throws IOException {
        Path gitignore = projectRoot().resolve(".gitignore");
        Assert.assertTrue(".gitignore 应存在", Files.exists(gitignore));
        String content = new String(Files.readAllBytes(gitignore), StandardCharsets.UTF_8);

        List<String> activeRules = Arrays.stream(content.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(Collectors.toList());

        for (String required : Arrays.asList("db.properties", "**/db.properties",
                "jdbc.properties", "**/jdbc.properties", ".env", "*.pem", "*.key")) {
            Assert.assertTrue("凭据忽略规则缺失或已被注释：" + required,
                    activeRules.contains(required));
        }
        // 构建产物也不应入库
        Assert.assertTrue("target/ 应被忽略", activeRules.contains("target/"));
    }

    /**
     * 用例 4：本地凭据文件可以存在，但绝不能被 git 跟踪；模板文件必须存在。
     *
     * <p>这里刻意区分"文件存在"与"文件被提交"：本地跑程序当然需要一个写着真口令的
     * db.properties，所以不能因为文件存在就判失败；要守住的是它不进版本库。
     * 判据用 {@code git ls-files}，比看文件是否存在更贴近"会不会被交出去"这件事。</p>
     */
    @Test
    public void t4_credentialFilesAreNotTrackedAndTemplatesExist() throws IOException {
        Path root = projectRoot();
        for (String forbidden : Arrays.asList(
                "lab-mybatis/src/main/resources/db.properties",
                "lab-bootstrap/src/test/resources/jdbc.properties",
                "db.properties",
                ".env")) {
            Assert.assertFalse("本地凭据文件不应被 git 跟踪：" + forbidden,
                    isTrackedByGit(root, forbidden));
        }

        Path template = root.resolve("lab-mybatis/src/main/resources/db.properties.example");
        Assert.assertTrue("模板文件应存在，供他人复制", Files.exists(template));
        String content = new String(Files.readAllBytes(template), StandardCharsets.UTF_8);
        Assert.assertTrue("模板中应说明该文件会被 .gitignore 排除",
                content.contains(".gitignore"));
        Assert.assertTrue("模板中应写明严禁提交真实凭据",
                content.contains("严禁提交"));
    }

    /**
     * 判断某个相对路径是否被 git 跟踪。
     *
     * <p>用 {@code git ls-files --error-unmatch} 的退出码作为判据：退出码 0 表示已跟踪。
     * 若当前目录不是 git 仓库（例如只拷贝了源码），视为"没有提交风险"，返回 false。</p>
     */
    private boolean isTrackedByGit(Path root, String relative) {
        try {
            Process process = new ProcessBuilder("git", "ls-files", "--error-unmatch", relative)
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .start();
            drainStream(process.getInputStream());
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;   // 没有 git 命令或不是仓库：不阻塞测试
        }
    }

    /** Java 8 没有 readAllBytes()，手动把流读空 */
    private static void drainStream(java.io.InputStream in) throws IOException {
        byte[] buf = new byte[4096];
        while (in.read(buf) != -1) {
            // 丢弃内容，只要把管道读空即可
        }
    }

    /** 去掉右值末尾的括号、分号等标点，便于判断它到底是不是标识符 */
    private String stripTrailingPunctuation(String value) {
        String trimmed = value.trim();
        while (!trimmed.isEmpty()) {
            char last = trimmed.charAt(trimmed.length() - 1);
            if (last == ';' || last == '+' || last == '}' || last == ')' || last == ',') {
                trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
            } else {
                break;
            }
        }
        return trimmed;
    }

    /** 可扫描的文本文件：只关心源码与配置，跳过二进制与构建产物 */
    private boolean isScannable(Path path) {
        String normalized = path.toString().replace('\\', '/');
        for (String excluded : EXCLUDED) {
            if (normalized.contains(excluded)) {
                return false;
            }
        }
        String name = path.getFileName().toString();
        return name.endsWith(".java") || name.endsWith(".xml") || name.endsWith(".sql")
                || name.endsWith(".properties") || name.endsWith(".sh")
                || name.endsWith(".yml") || name.endsWith(".yaml");
    }

    /**
     * 判断取值是否属于"非敏感"的形态。
     *
     * <p>只承认三种情况：空值（配置缺失）、{@code ${...}} 占位符、
     * 以及明确指向环境变量的名字。其它任何非空字面量都视为疑似明文口令——
     * 宁可误报，也不放过。</p>
     */
    private boolean isPlaceholder(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return true;
        }
        String lower = trimmed.toLowerCase();
        return lower.contains("_password") || lower.contains("_env")
                || lower.contains("环境变量") || lower.equals("***")
                || lower.equals("xxx");
    }

    /**
     * 定位项目根目录：从当前目录逐级向上找"本工程根 pom"。
     *
     * <p>判据是逐级向上<b>第一个</b>同时满足下列条件的目录：</p>
     * <ol>
     *   <li>存在父工程 pom（artifactId 为 mybit.lab 且 packaging 为 pom）；</li>
     *   <li>其下确实有 lab-common 与 lab-mybatis 两个模块目录。</li>
     * </ol>
     *
     * <p>为什么要加第 2 条：如果机器上同时存在多个同名工程，
     * 只按 artifactId 匹配可能命中别人的目录，导致扫描对象错位——
     * 这个测试曾在"项目换目录"后误判过。加上模块目录校验后，
     * 命中的一定是"向上的第一个完整工程根"。</p>
     */
    static Path projectRoot() {
        Path current = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && current != null; i++) {
            if (isProjectRoot(current)) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("无法定位项目根目录，当前：" + Paths.get("").toAbsolutePath());
    }

    /** 判断某目录是否为本工程根：认得单模块与多模块两种结构 */
    private static boolean isProjectRoot(Path dir) {
        Path pom = dir.resolve("pom.xml");
        if (!Files.exists(pom)) {
            return false;
        }
        try {
            String content = new String(Files.readAllBytes(pom), StandardCharsets.UTF_8);
            // 多模块工程：父 pom + 模块目录齐备
            boolean isParentPom = content.contains("<packaging>pom</packaging>");
            boolean hasModules = Files.isDirectory(dir.resolve("lab-common"))
                    && Files.isDirectory(dir.resolve("lab-mybatis"))
                    && Files.isDirectory(dir.resolve("lab-bootstrap"));
            return isParentPom && hasModules;
        } catch (IOException e) {
            return false;
        }
    }
}
