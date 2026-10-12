package com.lab.mapper;

import com.lab.common.dto.DeptStatVO;
import com.lab.common.entity.Emp;
import com.lab.common.entity.SysDept;
import com.lab.mybatis.config.MyBatisSessionFactory;
import com.lab.mybatis.mapper.EmpMapper;
import com.lab.mybatis.mapper.SysDeptMapper;
import com.lab.support.SqlCounter;
import org.apache.ibatis.session.SqlSession;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 部门统计与 N+1 对照测试。
 *
 * <p>本组用例把"N+1 到底慢在哪"量化成 SQL 条数：
 * 同一个业务结果用两种写法实现，断言<b>数据完全一致、SQL 条数相差 7 倍</b>。
 * 只看日志"感觉慢"无法作为结论，可断言的数字才可以。</p>
 *
 * <p>SQL 条数通过 {@link SqlCounter} 拦截 {@code StatementHandler#prepare} 统计，
 * 该拦截器只在测试内注册，生产配置里不存在。</p>
 */
public class DeptStatAndNPlusOneTest {

    /** 只在类加载时注册一次，测试之间用 reset() 清零 */
    private static final SqlCounter COUNTER = new SqlCounter();

    @BeforeClass
    public static void registerCounter() {
        MyBatisSessionFactory.getFactory()
                .getConfiguration().addInterceptor(COUNTER);
    }

    @Before
    public void resetCounter() {
        SqlCounter.reset();
    }

    @AfterClass
    public static void unregisterCounter() {
        removeInterceptorDirectly(COUNTER);
    }

    /**
     * 直接摘除拦截器：通过 Configuration 内部列表移除。
     *
     * <p>注意 {@code getInterceptors()} 返回的是不可变视图，不能直接 remove；
     * 这里用反射拿到原始 List。仅测试使用。</p>
     */
    private static void removeInterceptorDirectly(SqlCounter counter) {
        try {
            org.apache.ibatis.session.Configuration configuration =
                    MyBatisSessionFactory.getFactory().getConfiguration();
            java.lang.reflect.Field field = findField(configuration.getClass(), "interceptorChain");
            field.setAccessible(true);
            Object chain = field.get(configuration);
            java.lang.reflect.Field listField =
                    org.apache.ibatis.plugin.InterceptorChain.class.getDeclaredField("interceptors");
            listField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<org.apache.ibatis.plugin.Interceptor> interceptors =
                    (java.util.List<org.apache.ibatis.plugin.Interceptor>) listField.get(chain);
            // 重要：Configuration#getInterceptors() 返回的是不可变视图，
            // 必须操作 InterceptorChain 内部的原始 List 才能真的摘掉
            boolean removed = interceptors.remove(counter);
            System.out.println("[N+1] 计数插件已摘除：" + removed
                    + "，剩余拦截器数=" + interceptors.size());
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("[N+1] 拦截器清理失败：" + e.getClass().getSimpleName()
                    + " " + e.getMessage());
        }
    }

    /** 沿继承链查找字段：Configuration 的字段可能定义在父类上 */
    private static java.lang.reflect.Field findField(Class<?> type, String name)
            throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // 继续向父类查找
            }
        }
        throw new NoSuchFieldException(name);
    }

    /** 用例 1：JOIN 聚合一条 SQL 拿到全部 6 个部门的统计，逐字段核对 */
    @Test
    public void t1_joinAggregationReturnsAllDepartmentsInOneQuery() {
        try (SqlSession session = MyBatisSessionFactory.openSession()) {
            EmpMapper mapper = session.getMapper(EmpMapper.class);
            SqlCounter.reset();
            List<DeptStatVO> stats = mapper.selectDeptStatByJoin();
            long sqlCount = SqlCounter.count();

            Assert.assertEquals("应覆盖 6 个部门", 6, stats.size());
            Assert.assertEquals("JOIN 聚合应只用 1 条 SQL", 1L, sqlCount);

            DeptStatVO dev = stats.get(0);
            Assert.assertEquals(Integer.valueOf(1), dev.getDeptId());
            Assert.assertEquals("研发部", dev.getDeptName());
            Assert.assertEquals("研发部在职 2 人（许晴离职、邓涛已逻辑删除）",
                    Integer.valueOf(2), dev.getEmpCount());
            Assert.assertEquals(0, new BigDecimal("58000.00").compareTo(dev.getTotalSalary()));
            Assert.assertEquals(0, new BigDecimal("32000.00").compareTo(dev.getMaxSalary()));
            Assert.assertEquals(0, new BigDecimal("26000.00").compareTo(dev.getMinSalary()));
            Assert.assertEquals("平均薪资由代码计算，避免依赖数据库 AVG 的舍入",
                    0, new BigDecimal("29000.00").compareTo(dev.getAvgSalary()));
        }
    }

    /** 用例 2：N+1 写法必然发出 1 + 部门数 条 SQL */
    @Test
    public void t2_nPlusOneQueryIssuesOneSqlPerDepartment() {
        try (SqlSession session = MyBatisSessionFactory.openSession()) {
            EmpMapper mapper = session.getMapper(EmpMapper.class);
            SysDeptMapper deptMapper = session.getMapper(SysDeptMapper.class);

            SqlCounter.reset();
            List<SysDept> depts = deptMapper.selectAll();
            for (SysDept dept : depts) {
                mapper.selectDeptStatByDeptId(dept.getDeptId());
            }
            long sqlCount = SqlCounter.count();

            Assert.assertEquals(6, depts.size());
            Assert.assertEquals("1 条主查询 + 6 条子查询 = 7 条 SQL", 7L, sqlCount);
            System.out.println("[N+1] 逐部门查询 SQL 条数 = " + sqlCount);
        }
    }

    /** 用例 3：嵌套 &lt;collection select&gt; 的 N+1 同样产生 7 条 SQL */
    @Test
    public void t3_collectionSelectAlsoTriggersNPlusOne() {
        try (SqlSession session = MyBatisSessionFactory.openSession()) {
            EmpMapper mapper = session.getMapper(EmpMapper.class);
            SqlCounter.reset();
            List<SysDept> depts = mapper.selectDeptWithEmpsNPlusOne();
            long sqlCount = SqlCounter.count();

            Assert.assertEquals(6, depts.size());
            Assert.assertEquals("嵌套 select 写法应触发 N+1", 7L, sqlCount);
            Assert.assertTrue("每个部门的 emps 应已填充",
                    depts.stream().allMatch(d -> d.getEmps() != null));
        }
    }

    /** 用例 4：JOIN 优化写法只发 1 条 SQL，且数据与 N+1 完全一致 */
    @Test
    public void t4_joinOptimizationIsEquivalentButCheaper() {
        try (SqlSession session = MyBatisSessionFactory.openSession()) {
            EmpMapper mapper = session.getMapper(EmpMapper.class);

            SqlCounter.reset();
            List<SysDept> nPlusOne = mapper.selectDeptWithEmpsNPlusOne();
            long nPlusOneCount = SqlCounter.count();

            SqlCounter.reset();
            List<SysDept> joined = mapper.selectDeptWithEmpsJoin();
            long joinCount = SqlCounter.count();

            Assert.assertEquals("嵌套写法 7 条", 7L, nPlusOneCount);
            Assert.assertEquals("JOIN 写法 1 条", 1L, joinCount);

            // 数据必须完全一致：优化不能改变结果
            Assert.assertEquals(nPlusOne.size(), joined.size());
            for (int i = 0; i < nPlusOne.size(); i++) {
                SysDept a = nPlusOne.get(i);
                SysDept b = joined.get(i);
                Assert.assertEquals("部门主键应一致", a.getDeptId(), b.getDeptId());
                Assert.assertEquals("部门名称应一致", a.getDeptName(), b.getDeptName());
                Assert.assertEquals("员工列表应逐字段一致",
                        ids(a.getEmps()), ids(b.getEmps()));
            }
            System.out.println("[N+1] 对照：嵌套=" + nPlusOneCount
                    + " 条，JOIN=" + joinCount + " 条");
        }
    }

    /** 用例 5：单部门统计与 JOIN 聚合的对应行完全一致（口径交叉验证） */
    @Test
    public void t5_singleDeptStatMatchesJoinRow() {
        try (SqlSession session = MyBatisSessionFactory.openSession()) {
            EmpMapper mapper = session.getMapper(EmpMapper.class);
            List<DeptStatVO> all = mapper.selectDeptStatByJoin();

            for (DeptStatVO expected : all) {
                DeptStatVO actual = mapper.selectDeptStatByDeptId(expected.getDeptId());
                Assert.assertNotNull("每个部门都应有统计行：" + expected.getDeptId(), actual);
                Assert.assertEquals("人数口径应一致", expected.getEmpCount(), actual.getEmpCount());
                Assert.assertEquals("薪资合计口径应一致",
                        0, expected.getTotalSalary().compareTo(actual.getTotalSalary()));
                Assert.assertEquals("最高薪资口径应一致",
                        0, expected.getMaxSalary().compareTo(actual.getMaxSalary()));
            }
        }
    }

    /** 用例 6：手写统计与逐行求和交叉验证，防止 GROUP BY 口径漂移 */
    @Test
    public void t6_aggregationEqualsRowByRowSum() {
        try (SqlSession session = MyBatisSessionFactory.openSession()) {
            EmpMapper mapper = session.getMapper(EmpMapper.class);
            List<DeptStatVO> stats = mapper.selectDeptStatByJoin();

            List<Emp> onJob = mapper.selectByCondition(onJobQuery());
            BigDecimal rowByRow = onJob.stream().map(Emp::getSalary)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal aggregated = stats.stream().map(DeptStatVO::getTotalSalary)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int rowCount = onJob.size();
            int aggregatedCount = stats.stream()
                    .mapToInt(s -> s.getEmpCount() == null ? 0 : s.getEmpCount()).sum();

            Assert.assertEquals("人数应一致", rowCount, aggregatedCount);
            Assert.assertEquals("薪资合计应一致", 0, rowByRow.compareTo(aggregated));
            Assert.assertEquals("在职员工应为 12 人", 12, rowCount);
        }
    }

    /** 用例 7：LEFT JOIN 保证"没有在职员工的部门"仍然出现，计数为 0 */
    @Test
    public void t7_leftJoinKeepsEmptyDepartments() {
        final int deptId = 1;
        List<Emp> members;
        try (SqlSession session = MyBatisSessionFactory.openSession()) {
            EmpMapper mapper = session.getMapper(EmpMapper.class);
            SysDeptMapper deptMapper = session.getMapper(SysDeptMapper.class);

            List<SysDept> allDepts = deptMapper.selectAll();
            List<DeptStatVO> stats = mapper.selectDeptStatByJoin();
            Assert.assertEquals("统计行数应等于部门总数", allDepts.size(), stats.size());

            members = mapper.selectEmpsByDeptId(deptId);
            Assert.assertFalse("研发部应有在职员工", members.isEmpty());
        }

        // 第一步：把研发部的在职员工全部置为离职并提交
        try (SqlSession writeSession = MyBatisSessionFactory.openSession()) {
            EmpMapper writeMapper = writeSession.getMapper(EmpMapper.class);
            for (Emp m : members) {
                Emp patch = new Emp();
                patch.setEmpId(m.getEmpId());
                patch.setStatus(0);
                writeMapper.updateDynamic(patch);
            }
            writeSession.commit();
        }

        // 第二步：用全新会话观察结果（已提交的数据对旧会话的快照不可见）
        Integer empCount = null;
        BigDecimal totalSalary = null;
        Integer joinCount = null;
        try (SqlSession session = MyBatisSessionFactory.openSession()) {
            EmpMapper mapper = session.getMapper(EmpMapper.class);
            DeptStatVO after = mapper.selectDeptStatByDeptId(deptId);
            Assert.assertNotNull("部门不应因为没人而消失", after);
            empCount = after.getEmpCount();
            totalSalary = after.getTotalSalary();

            DeptStatVO inJoin = mapper.selectDeptStatByJoin().stream()
                    .filter(s -> s.getDeptId().equals(deptId)).findFirst().orElse(null);
            Assert.assertNotNull("汇总结果里也应有该部门", inJoin);
            joinCount = inJoin.getEmpCount();
        } finally {
            // 第三步：无论断言是否通过都先复原数据，避免污染基线
            try (SqlSession restore = MyBatisSessionFactory.openSession()) {
                EmpMapper restoreMapper = restore.getMapper(EmpMapper.class);
                for (Emp m : members) {
                    Emp patch = new Emp();
                    patch.setEmpId(m.getEmpId());
                    patch.setStatus(1);
                    restoreMapper.updateDynamic(patch);
                }
                restore.commit();
            }
        }

        Assert.assertEquals("人数应归零", Integer.valueOf(0), empCount);
        Assert.assertEquals("薪资合计应归零", 0, BigDecimal.ZERO.compareTo(totalSalary));
        Assert.assertEquals("汇总行同样应归零", Integer.valueOf(0), joinCount);
    }

    private static List<Integer> ids(List<Emp> emps) {
        return emps.stream().map(Emp::getEmpId).collect(Collectors.toList());
    }

    private static com.lab.common.dto.EmpQuery onJobQuery() {
        com.lab.common.dto.EmpQuery q = new com.lab.common.dto.EmpQuery();
        q.setStatus(1);
        return q;
    }
}
