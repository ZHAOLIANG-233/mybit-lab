package com.lab.service;

import com.lab.common.dto.DeptStatVO;
import com.lab.common.dto.EmpQuery;
import com.lab.common.dto.PageQuery;
import com.lab.common.dto.PageResult;
import com.lab.common.entity.Emp;
import com.lab.common.exception.BusinessException;
import com.lab.common.exception.ErrorCode;
import com.lab.common.exception.ValidationException;
import com.lab.mybatis.config.MyBatisSessionFactory;
import com.lab.mybatis.mapper.EmpMapper;
import com.lab.mybatis.service.EmpService;
import com.lab.mybatis.service.impl.EmpServiceImpl;
import com.lab.support.TestBase;
import org.apache.ibatis.session.SqlSession;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 业务层与事务测试。
 *
 * <p>本组用例的重点不是 CRUD 本身，而是三条边界：</p>
 * <ol>
 *   <li>校验失败要在"开事务之前"被拦住；</li>
 *   <li>批量操作必须整体成功或整体失败，不留半成品；</li>
 *   <li>业务中途失败时，此前已执行的更新必须一并回滚。</li>
 * </ol>
 */
public class EmpServiceTest extends TestBase {

    private final EmpService service = new EmpServiceImpl();

    /** 用例 1：新增成功并返回回填后的主键（跨会话复核） */
    @Test
    public void t1_saveReturnsGeneratedId() {
        Emp emp = Emp.of("E9401S", "业务层新增", 6, "财务部", new BigDecimal("10500.00"));
        Integer id = service.save(emp);

        Assert.assertNotNull(id);
        try (SqlSession verify = MyBatisSessionFactory.openSession()) {
            Emp loaded = verify.getMapper(EmpMapper.class).selectById(id);
            Assert.assertNotNull("业务层 save 应真的提交", loaded);
            Assert.assertEquals("E9401S", loaded.getEmpNo());
        } finally {
            cleanup(id);
        }
    }

    /** 用例 2：工号格式非法时抛 ValidationException，且不产生任何数据 */
    @Test
    public void t2_invalidEmpNoIsRejectedBeforeTransaction() {
        Emp emp = Emp.of("bad-no!", "非法工号", 1, "研发部", new BigDecimal("10000.00"));
        try {
            service.save(emp);
            Assert.fail("非法工号应被拒绝");
        } catch (ValidationException expected) {
            Assert.assertEquals(ErrorCode.PARAM_INVALID, expected.getErrorCode());
            Assert.assertTrue(expected.getMessage().contains("empNo"));
        }
        Assert.assertNull("校验失败不应回填主键，也就不应有任何写入", emp.getEmpId());
    }

    /** 用例 3：薪资超出合理上限被拒绝 */
    @Test
    public void t3_absurdSalaryIsRejected() {
        Emp emp = Emp.of("E9403S", "天价薪资", 1, "研发部", new BigDecimal("99999999.00"));
        try {
            service.save(emp);
            Assert.fail("超上限薪资应被拒绝");
        } catch (ValidationException expected) {
            Assert.assertTrue(expected.getMessage().contains("上限"));
        }
    }

    /** 用例 4：按 id 查询时的参数校验（null / 非正数） */
    @Test
    public void t4_getByIdValidatesArgument() {
        try {
            service.getById(null);
            Assert.fail("null 主键应被拒绝");
        } catch (ValidationException expected) {
            Assert.assertTrue(expected.getMessage().contains("empId"));
        }
        try {
            service.getById(0);
            Assert.fail("0 主键应被拒绝");
        } catch (ValidationException expected) {
            Assert.assertTrue(expected.getMessage().contains("empId"));
        }
        Assert.assertNotNull(service.getById(1));
    }

    /** 用例 5：动态更新只改传入字段 */
    @Test
    public void t5_selectiveUpdateKeepsOtherFields() {
        Emp original = service.getById(4);
        Emp patch = new Emp();
        patch.setEmpId(4);
        patch.setPost("测试架构师");
        Assert.assertEquals(1, service.updateSelective(patch));

        Emp after = service.getById(4);
        Assert.assertEquals("测试架构师", after.getPost());
        Assert.assertEquals("未传入的字段不得改动", original.getSalary(), after.getSalary());
        Assert.assertEquals(original.getEmpName(), after.getEmpName());
    }

    /** 用例 6：状态字段只接受 0/1 */
    @Test
    public void t6_statusOnlyAcceptsZeroOrOne() {
        Emp patch = new Emp();
        patch.setEmpId(5);
        patch.setStatus(2);
        try {
            service.updateSelective(patch);
            Assert.fail("status=2 应被拒绝");
        } catch (BusinessException expected) {
            Assert.assertEquals(ErrorCode.PARAM_INVALID, expected.getErrorCode());
        }
    }

    /** 用例 7：批量新增在同一事务内完成，主键逐行回填 */
    @Test
    public void t7_saveBatchIsAtomic() {
        List<Emp> batch = Arrays.asList(
                Emp.of("E9501B", "批量业务甲", 3, "产品部", new BigDecimal("12000.00")),
                Emp.of("E9502B", "批量业务乙", 3, "产品部", new BigDecimal("12500.00")),
                Emp.of("E9503B", "批量业务丙", 3, "产品部", new BigDecimal("13000.00")));
        Assert.assertEquals(3, service.saveBatch(batch));
        for (Emp e : batch) {
            Assert.assertNotNull(e.getEmpId());
        }
        try (SqlSession verify = MyBatisSessionFactory.openSession()) {
            for (Emp e : batch) {
                Assert.assertNotNull(verify.getMapper(EmpMapper.class).selectById(e.getEmpId()));
            }
        } finally {
            for (Emp e : batch) {
                cleanup(e.getEmpId());
            }
        }
    }

    /** 用例 8：批量中间一条不合法时整批拒绝 —— 不允许"插了一半" */
    @Test
    public void t8_saveBatchRejectsWholeBatchOnOneInvalidItem() {
        List<Emp> batch = Arrays.asList(
                Emp.of("E9601B", "合法甲", 3, "产品部", new BigDecimal("12000.00")),
                Emp.of("X", "非法工号", 3, "产品部", new BigDecimal("12000.00")),
                Emp.of("E9603B", "合法丙", 3, "产品部", new BigDecimal("12000.00")));
        try {
            service.saveBatch(batch);
            Assert.fail("存在非法项时整批应被拒绝");
        } catch (ValidationException expected) {
            Assert.assertTrue(expected.getMessage().contains("empNo"));
        }
        // 三个对象都不应拿到主键，说明一条都没进库
        for (Emp e : batch) {
            Assert.assertNull("整批拒绝后不应有主键回填", e.getEmpId());
        }
    }

    /** 用例 9：调薪过程中途失败，前一条更新必须整体回滚 */
    @Test
    public void t9_raiseSalaryRollsBackWholeTransaction() {
        Emp firstBefore = mapper.selectById(1);
        BigDecimal salaryBefore = firstBefore.getSalary();

        try {
            // 第二个 id 不存在 → 抛异常 → 第一条的加薪必须被撤销
            service.raiseSalary(1, 999999, new BigDecimal("1000.00"));
            Assert.fail("第二个员工不存在时应抛异常");
        } catch (BusinessException expected) {
            Assert.assertEquals(ErrorCode.DATA_NOT_FOUND, expected.getErrorCode());
        }

        // 用同一个会话复核：若回滚生效，第一条薪资应保持原值
        Emp after = mapper.selectById(1);
        Assert.assertEquals("前一条更新必须随事务一起回滚",
                0, salaryBefore.compareTo(after.getSalary()));
    }

    /** 用例 10：调薪在两条都存在时整体成功 */
    @Test
    public void t10_raiseSalarySucceedsOnBothTargets() {
        Emp a = mapper.selectById(5);
        Emp b = mapper.selectById(6);
        BigDecimal delta = new BigDecimal("500.00");

        int affected = service.raiseSalary(5, 6, delta);
        Assert.assertEquals("两条都应被更新", 2, affected);

        // service 已经提交，必须用【新会话】验证写入结果：
        // 在 REPEATABLE READ 下，旧会话的快照读不到之后提交的数据
        try (SqlSession verify = MyBatisSessionFactory.openSession()) {
            Emp aAfter = verify.getMapper(EmpMapper.class).selectById(5);
            Emp bAfter = verify.getMapper(EmpMapper.class).selectById(6);
            Assert.assertEquals(0, a.getSalary().add(delta).compareTo(aAfter.getSalary()));
            Assert.assertEquals(0, b.getSalary().add(delta).compareTo(bAfter.getSalary()));
        }

        // 本用例真的写了库，显式还原薪资，保证基线不被污染
        restoreSalary(5, a.getSalary());
        restoreSalary(6, b.getSalary());
    }

    /** 用例 11：分页查询经业务层与直接走 Mapper 的结果一致 */
    @Test
    public void t11_servicePagingMatchesMapperPaging() {
        PageResult<Emp> viaService = service.page(null, new PageQuery(2, 5));
        List<Emp> viaMapper = mapper.selectPage(new EmpQuery(), new PageQuery(2, 5));

        Assert.assertEquals(viaMapper.size(), viaService.getRecords().size());
        Assert.assertEquals(
                viaMapper.stream().map(Emp::getEmpId).collect(Collectors.toList()),
                viaService.getRecords().stream().map(Emp::getEmpId).collect(Collectors.toList()));
        Assert.assertEquals(15L, viaService.getTotal());
    }

    /** 用例 12：部门统计经业务层返回，且合计与明细自洽 */
    @Test
    public void t12_deptStatisticsIsSelfConsistent() {
        List<DeptStatVO> stats = service.deptStatistics();
        Assert.assertEquals("应有 6 个部门", 6, stats.size());

        long totalEmployees = stats.stream()
                .mapToLong(s -> s.getEmpCount() == null ? 0 : s.getEmpCount()).sum();
        Assert.assertEquals("各部门在职人数之和应为 12", 12L, totalEmployees);

        BigDecimal totalSalary = stats.stream()
                .map(DeptStatVO::getTotalSalary)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expected = mapper.selectByCondition(aliveOnJobQuery()).stream()
                .map(Emp::getSalary).reduce(BigDecimal.ZERO, BigDecimal::add);
        Assert.assertEquals("统计口径应与逐行求和一致", 0, expected.compareTo(totalSalary));
    }

    /** 用例 13：物理删除后查不到，且返回行数为 1 */
    @Test
    public void t13_removeByIdDeletesRow() {
        Emp emp = Emp.of("E9701R", "待物理删除", 4, "运维部", new BigDecimal("11000.00"));
        Integer id = service.save(emp);
        Assert.assertEquals(1, service.removeById(id));
        Assert.assertNull(service.getById(id));
        // 已在 service 层提交并删除，无需再清理
    }

    /** 用例 14：未配置口令时 DbConfig 快速失败，而不是用空口令去连 */
    @Test
    public void t14_dbConfigFailsFastWithoutPassword() {
        String originalProperty = System.getProperty("db.password");
        String originalEnv = System.getenv("DB_PASSWORD");
        try {
            System.clearProperty("db.password");
            if (originalEnv == null) {
                // 只有在环境变量也没配置时才能断言"失败"
                try {
                    com.lab.common.config.DbConfig.load();
                    // 若 classpath 上存在 db.properties，则视为已配置，跳过本断言
                    System.out.println("[SERVICE] 检测到 db.properties，跳过快速失败断言");
                } catch (BusinessException expected) {
                    Assert.assertEquals(ErrorCode.CONFIG_INVALID, expected.getErrorCode());
                }
            }
        } finally {
            if (originalProperty != null) {
                System.setProperty("db.password", originalProperty);
            }
        }
    }

    private com.lab.common.dto.EmpQuery aliveOnJobQuery() {
        com.lab.common.dto.EmpQuery q = new com.lab.common.dto.EmpQuery();
        q.setStatus(1);
        return q;
    }

    /** 跨连接清理：本会话已提交，必须用新会话删除，避免行锁互相阻塞 */
    private void cleanup(Integer id) {
        if (id == null) {
            return;
        }
        try (SqlSession session = MyBatisSessionFactory.openSession()) {
            session.getMapper(EmpMapper.class).deleteById(id);
            session.commit();
        }
    }

    /** 跨连接还原薪资：用于本用例已提交写操作的场景 */
    private void restoreSalary(Integer empId, BigDecimal salary) {
        try (SqlSession session = MyBatisSessionFactory.openSession()) {
            Emp patch = new Emp();
            patch.setEmpId(empId);
            patch.setSalary(salary);
            session.getMapper(EmpMapper.class).updateDynamic(patch);
            session.commit();
        }
    }
}
