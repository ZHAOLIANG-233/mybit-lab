package com.lab;

import com.lab.common.dto.DeptStatVO;
import com.lab.common.dto.EmpQuery;
import com.lab.common.dto.PageQuery;
import com.lab.common.dto.PageResult;
import com.lab.common.entity.Emp;
import com.lab.common.entity.SysDept;
import com.lab.mybatis.config.MyBatisSessionFactory;
import com.lab.mybatis.config.MyBatisSessionTemplate;
import com.lab.mybatis.mapper.EmpMapper;
import com.lab.mybatis.mapper.SysDeptMapper;
import com.lab.mybatis.service.EmpService;
import com.lab.mybatis.service.impl.EmpServiceImpl;
import org.apache.ibatis.session.SqlSession;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * 实验一运行入口：直接在 IDEA 里右键 Run 即可看到两个表的完整演示。
 *
 * <p>演示顺序（全部只读，不改动数据库里的基线数据）：</p>
 * <ol>
 *   <li>连接与表结构：确认 ssm_emp 库、emp 与 sys_dept 两张表都在；</li>
 *   <li>sys_dept 表：6 个部门逐行打印；</li>
 *   <li>emp 表：16 名员工（含 1 条已逻辑删除）分页打印；</li>
 *   <li>条件查询：多条件动态 SQL + 排序白名单；</li>
 *   <li>分页：COUNT + LIMIT 两条 SQL 的结果；</li>
 *   <li>部门统计：JOIN 聚合 vs N+1，两种写法结果一致、SQL 条数不同；</li>
 *   <li>MyBatis-Plus 轨：BaseMapper 通用 CRUD 与逻辑删除的效果；</li>
 *   <li>写操作演示：插入 → 更新 → 删除，最后回滚，基线不受影响。</li>
 * </ol>
 */
public class LabApplication {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        // 演示时把 SQL 明细关掉，只用简洁模式；想看 SQL 就把这一行注释掉
        quietLogging();
        banner();
        checkConnectionAndSchema();
        showDeptTable();
        showEmpTable();
        showConditionQuery();
        showPagination();
        showDeptStatistics();
        showNPlusOneComparison();
        showMyBatisPlus();
        showWriteAndRollback();
        summary();
    }

    // ================= 各个演示环节 =================

    /** 换成演示用的日志配置，避免 SQL 明细把控制台刷满 */
    private static void quietLogging() {
        try (java.io.InputStream in = LabApplication.class.getClassLoader()
                .getResourceAsStream("log4j-demo.properties")) {
            if (in != null) {
                java.util.Properties props = new java.util.Properties();
                props.load(in);
                org.apache.log4j.PropertyConfigurator.configure(props);
            }
        } catch (Exception e) {
            // 配置读不到就用默认的，不影响演示
        }
    }

    private static void banner() {
        System.out.println();
        System.out.println("==================================================");
        System.out.println("  Java 框架技术（一）· 实验一");
        System.out.println("  MyBatis / MyBatis-Plus 环境搭建与 CRUD 实操");
        System.out.println("  mybit.lab · 赵亮 20241850 · 4 班");
        System.out.println("==================================================");
        System.out.println();
    }

    /** 1. 连接 + 表结构：两个表都得在 */
    private static void checkConnectionAndSchema() throws Exception {
        section("1. 数据库连接与表结构");
        try (SqlSession session = MyBatisSessionFactory.openSession();
             Connection conn = session.getConnection();
             Statement stmt = conn.createStatement()) {

            System.out.println("  数据库：" + conn.getCatalog()
                    + "    版本：" + conn.getMetaData().getDatabaseProductVersion());

            int empRows = count(stmt, "SELECT COUNT(*) FROM emp");
            int deptRows = count(stmt, "SELECT COUNT(*) FROM sys_dept");
            System.out.println("  emp      ：" + empRows + " 行");
            System.out.println("  sys_dept ：" + deptRows + " 行");

            listColumns(stmt, "emp");
            listColumns(stmt, "sys_dept");
            check("两个表都已建好", empRows > 0 && deptRows > 0);
        } catch (Exception e) {
            fail("连接失败：" + e.getMessage());
            System.out.println();
            System.out.println("  请先确认：");
            System.out.println("    1) 数据库已启动：bash scripts/start-lab-mysql.sh");
            System.out.println("    2) 已导入表结构：mysql -u root -p < sql/01_schema.sql");
            System.out.println("    3) 已导入数据  ：mysql -u root -p < sql/02_seed.sql");
            System.out.println("    4) 口令已配置：-Ddb.password=你的口令 或环境变量 DB_PASSWORD");
            throw e;
        }
    }

    /** 2. sys_dept 表 */
    private static void showDeptTable() {
        section("2. sys_dept 表（部门维表，6 条）");
        try (SqlSession session = MyBatisSessionFactory.openSession()) {
            SysDeptMapper mapper = session.getMapper(SysDeptMapper.class);
            List<SysDept> depts = mapper.selectAll();
            System.out.printf("  %-8s %-10s %-10s %-10s %-12s%n",
                    "dept_id", "dept_code", "dept_name", "location", "budget");
            System.out.println("  " + repeatChar('-', 62));
            for (SysDept d : depts) {
                System.out.printf("  %-8d %-10s %-10s %-10s %-12s%n",
                        d.getDeptId(), d.getDeptCode(), d.getDeptName(),
                        nvl(d.getLocation()), d.getBudget());
            }
            check("sys_dept 返回 6 条", depts.size() == 6);
            check("按主键查 D01 能命中", mapper.selectByCode("D01") != null);
        }
    }

    /** 3. emp 表 */
    private static void showEmpTable() {
        section("3. emp 表（员工表，16 条，含 1 条已逻辑删除）");
        try (SqlSession session = MyBatisSessionFactory.openSession()) {
            EmpMapper mapper = session.getMapper(EmpMapper.class);
            List<Emp> all = mapper.selectAll();
            System.out.printf("  %-7s %-9s %-6s %-5s %-8s %-9s %-6s %-8s%n",
                    "emp_id", "emp_no", "姓名", "性别", "部门", "岗位", "薪资", "状态");
            System.out.println("  " + repeatChar('-', 78));
            for (Emp e : all) {
                System.out.printf("  %-7d %-9s %-6s %-5s %-8s %-9s %-6s %-8s%n",
                        e.getEmpId(), e.getEmpNo(), e.getEmpName(), e.getGender(),
                        nvl(e.getDept()), nvl(e.getPost()), e.getSalary(), status(e));
            }
            long deleted = all.stream().filter(x -> Integer.valueOf(1).equals(x.getDelFlag())).count();
            check("emp 物理共 16 行", all.size() == 16);
            check("其中已逻辑删除 1 行", deleted == 1);
            check("主键 1 号是张伟", "张伟".equals(mapper.selectById(1).getEmpName()));
        }
    }

    /** 4. 多条件查询 */
    private static void showConditionQuery() {
        section("4. 多条件查询（动态 SQL）");
        EmpQuery q = new EmpQuery();
        q.setDeptId(1);
        q.setStatus(1);
        q.setMinSalary(new BigDecimal("20000"));

        try (SqlSession session = MyBatisSessionFactory.openSession()) {
            EmpMapper mapper = session.getMapper(EmpMapper.class);
            List<Emp> list = mapper.selectByCondition(q);
            System.out.println("  条件：dept_id = 1 AND status = 1 AND salary >= 20000");
            for (Emp e : list) {
                System.out.printf("    %-6s %-10s %s%n", e.getEmpId(), e.getEmpName(), e.getSalary());
            }
            check("研发部在职且薪资 >= 20000 的正好 2 人", list.size() == 2);
            check("计数与列表口径一致", mapper.countByCondition(q) == list.size());
        }
    }

    /** 5. 分页 */
    private static void showPagination() {
        section("5. 分页查询（COUNT + LIMIT 两条 SQL）");
        EmpService service = new EmpServiceImpl();
        EmpQuery q = new EmpQuery();
        q.setOrderBy("salary");
        q.setAsc(Boolean.FALSE);

        PageResult<Emp> page1 = service.page(q, new PageQuery(1, 4));
        System.out.println("  按薪资降序，第 1 页（每页 4 条）：");
        for (Emp e : page1.getRecords()) {
            System.out.printf("    %-6s %-10s %s%n", e.getEmpId(), e.getEmpName(), e.getSalary());
        }
        System.out.printf("  总数=%d  总页数=%d  当前页=%d  还有下一页=%s%n",
                page1.getTotal(), page1.getPages(), page1.getPageNum(), page1.isHasNext());
        check("总数 15 行（已逻辑删除的不算）", page1.getTotal() == 15);
        check("15 / 4 向上取整为 4 页", page1.getPages() == 4);
        check("第 1 页返回 4 条", page1.getRecords().size() == 4);
    }

    /** 6. 部门统计：JOIN 聚合 */
    private static void showDeptStatistics() {
        section("6. 部门统计（JOIN 聚合，一次查询）");
        EmpService service = new EmpServiceImpl();
        List<DeptStatVO> stats = service.deptStatistics();
        System.out.printf("  %-8s %-10s %-8s %-12s %-10s%n",
                "dept_id", "部门", "在职人数", "薪资合计", "平均薪资");
        System.out.println("  " + repeatChar('-', 56));
        long total = 0;
        for (DeptStatVO s : stats) {
            System.out.printf("  %-8d %-10s %-8d %-12s %-10s%n",
                    s.getDeptId(), s.getDeptName(), s.getEmpCount(),
                    s.getTotalSalary(), s.getAvgSalary());
            total += s.getEmpCount() == null ? 0 : s.getEmpCount();
        }
        check("6 个部门都有统计行", stats.size() == 6);
        check("在职人数合计 12 人", total == 12);
    }

    /** 7. N+1 对照 */
    private static void showNPlusOneComparison() {
        section("7. N+1 对照（数据一样，SQL 条数不一样）");
        try (SqlSession session = MyBatisSessionFactory.openSession()) {
            EmpMapper mapper = session.getMapper(EmpMapper.class);

            List<SysDept> nPlusOne = mapper.selectDeptWithEmpsNPlusOne();
            int nPlusOneEmps = nPlusOne.stream().mapToInt(d -> d.getEmps().size()).sum();

            List<SysDept> joined = mapper.selectDeptWithEmpsJoin();
            int joinedEmps = joined.stream().mapToInt(d -> d.getEmps().size()).sum();

            System.out.println("  嵌套 select 写法：1 条主查询 + 6 条子查询 = 7 条 SQL");
            System.out.println("  LEFT JOIN  写法：1 条 SQL 全部取回");
            System.out.printf("  两种写法取到的员工数：%d vs %d%n", nPlusOneEmps, joinedEmps);
            check("两种写法的数据完全一致", nPlusOneEmps == joinedEmps);
            check("都取到 12 名在职员工", joinedEmps == 12);
        }
    }

    /** 8. MyBatis-Plus 轨 */
    private static void showMyBatisPlus() throws Exception {
        section("8. MyBatis-Plus 轨（BaseMapper + @TableLogic）");
        try (SqlSession session = com.lab.plus.config.PlusSessionFactory.openSession()) {
            com.lab.plus.mapper.EmpPlusMapper mapper =
                    session.getMapper(com.lab.plus.mapper.EmpPlusMapper.class);

            long alive = mapper.selectCount(null);
            long total;
            try {
                try (Statement stmt = session.getConnection().createStatement()) {
                    total = count(stmt, "SELECT COUNT(*) FROM emp");
                }
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException("统计物理行数失败", e);
            }

            System.out.println("  selectCount(null)        → " + alive + "（自动带上 del_flag = 0）");
            System.out.println("  直接查物理行数           → " + total + "（含 1 条已逻辑删除）");
            check("MP 自动过滤掉逻辑删除行", alive == 15);
            check("物理行仍是 16", total == 16);

            Emp plusOne = mapper.selectById(1);
            check("MP selectById 能查到 1 号", plusOne != null);
            check("@TableLogic 标在 del_flag 上",
                    Integer.valueOf(0).equals(plusOne.getDelFlag()));
        }
    }

    /** 9. 写操作：增、改、删，最后回滚 */
    private static void showWriteAndRollback() {
        section("9. 写操作演示（插入 → 更新 → 删除，最后回滚，基线不变）");
        final Integer[] newId = new Integer[1];
        MyBatisSessionTemplate template = new MyBatisSessionTemplate();

        try {
            template.withSession(session -> {
                EmpMapper mapper = session.getMapper(EmpMapper.class);
                Emp emp = Emp.of("E9000Z", "演示用员工", 5, "人事部", new BigDecimal("8888.00"));
                mapper.insert(emp);
                newId[0] = emp.getEmpId();
                System.out.println("  插入：" + emp.getEmpNo() + "，回填主键 emp_id = " + emp.getEmpId());

                Emp patch = new Emp();
                patch.setEmpId(emp.getEmpId());
                patch.setPost("演示用岗位");
                mapper.updateDynamic(patch);
                System.out.println("  更新：只改 post，其它字段不动 → "
                        + mapper.selectById(emp.getEmpId()).getPost());

                mapper.deleteById(emp.getEmpId());
                System.out.println("  删除：物理删除刚插入的这行");

                // 故意抛异常，让整个事务回滚，这样不会在库里留下痕迹
                throw new IllegalStateException("演示结束，主动回滚");
            });
        } catch (IllegalStateException expected) {
            System.out.println("  事务已回滚（" + expected.getMessage() + "）");
        }

        try (SqlSession verify = MyBatisSessionFactory.openSession()) {
            Emp leftover = verify.getMapper(EmpMapper.class).selectById(newId[0]);
            check("回滚后库里没有留下演示数据", leftover == null);
            check("emp 表仍然是 16 行",
                    verify.getMapper(EmpMapper.class).selectAll().size() == 16);
        }
    }

    private static void summary() {
        System.out.println();
        System.out.println("==================================================");
        System.out.println("  演示结束：通过 " + passed + " 项，失败 " + failed + " 项");
        if (failed == 0) {
            System.out.println("  两个表（emp / sys_dept）工作正常，CRUD、动态 SQL、");
            System.out.println("  分页、统计与 MyBatis-Plus 均可用。");
        } else {
            System.out.println("  有失败项，请看上面的提示逐条排查。");
        }
        System.out.println("==================================================");
        System.out.println();
    }

    // ================= 小工具 =================

    private static void section(String title) {
        System.out.println();
        System.out.println("【" + title + "】");
    }

    private static void check(String what, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  [OK]   " + what);
        } else {
            failed++;
            System.out.println("  [FAIL] " + what);
        }
    }

    private static void fail(String msg) {
        failed++;
        System.out.println("  [FAIL] " + msg);
    }

    private static String status(Emp e) {
        if (Integer.valueOf(1).equals(e.getDelFlag())) {
            return "已删除";
        }
        return Integer.valueOf(1).equals(e.getStatus()) ? "在职" : "离职";
    }

    /** Java 8 没有 String.repeat，自己拼一个 */
    private static String repeatChar(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    private static String nvl(Object v) {
        return v == null ? "-" : String.valueOf(v);
    }

    private static int count(Statement stmt, String sql) throws Exception {
        try (ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void listColumns(Statement stmt, String table) throws Exception {
        StringBuilder cols = new StringBuilder();
        int n = 0;
        try (ResultSet rs = stmt.executeQuery(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + table
                        + "' ORDER BY ORDINAL_POSITION")) {
            while (rs.next()) {
                if (n++ > 0) {
                    cols.append(", ");
                }
                cols.append(rs.getString(1));
            }
        }
        System.out.println("  " + table + " 的列（" + n + " 个）：" + cols);
    }
}
