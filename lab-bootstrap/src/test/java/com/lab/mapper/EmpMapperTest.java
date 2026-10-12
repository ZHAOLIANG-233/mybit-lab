package com.lab.mapper;

import com.lab.common.entity.Emp;
import com.lab.mybatis.mapper.EmpMapper;
import com.lab.support.TestBase;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 原生 MyBatis 基础 CRUD 测试。
 *
 * <p>每个用例都在基类提供的"已开启事务的会话"里执行，方法结束统一回滚，
 * 因此可以放心地插入、修改、删除，而不污染数据基线。</p>
 */
public class EmpMapperTest extends TestBase {

    /** 用例 1：主键查询命中，逐字段核对映射完整性 */
    @Test
    public void t1_selectByIdMapsEveryField() {
        Emp emp = mapper.selectById(1);
        Assert.assertNotNull("emp_id=1 应存在", emp);
        Assert.assertEquals(Integer.valueOf(1), emp.getEmpId());
        Assert.assertEquals("E2015001", emp.getEmpNo());
        Assert.assertEquals("张伟", emp.getEmpName());
        Assert.assertEquals("男", emp.getGender());
        Assert.assertEquals(LocalDate.of(1990, 3, 12), emp.getBirthDate());
        Assert.assertEquals("zhangwei@lab.com", emp.getEmail());
        Assert.assertEquals("13800000001", emp.getPhone());
        Assert.assertEquals(Integer.valueOf(1), emp.getDeptId());
        Assert.assertEquals("研发部", emp.getDept());
        Assert.assertEquals("架构师", emp.getPost());
        Assert.assertEquals(0, new BigDecimal("32000.00").compareTo(emp.getSalary()));
        Assert.assertEquals(LocalDate.of(2015, 7, 1), emp.getHireDate());
        Assert.assertEquals(Integer.valueOf(1), emp.getStatus());
        Assert.assertEquals(Integer.valueOf(0), emp.getDelFlag());
        Assert.assertNotNull("version 不应为 null", emp.getVersion());
    }

    /** 用例 2：主键不存在时返回 null，而不是抛异常 */
    @Test
    public void t2_selectByIdReturnsNullForMissingRow() {
        Assert.assertNull(mapper.selectById(999999));
    }

    /** 用例 3：全量查询返回全部物理行（含 1 条已逻辑删除），主键升序 */
    @Test
    public void t3_selectAllIncludesLogicallyDeletedRow() {
        List<Emp> all = mapper.selectAll();
        Assert.assertEquals("原生 MyBatis 轨不做逻辑删除过滤，应返回全部物理行",
                16, all.size());
        long deleted = all.stream().filter(e -> Integer.valueOf(1).equals(e.getDelFlag())).count();
        Assert.assertEquals("应有且仅有 1 条已逻辑删除的记录", 1L, deleted);
        Assert.assertEquals("emp_id=16 的那条就是逻辑删除行",
                Integer.valueOf(16), all.get(all.size() - 1).getEmpId());
    }

    /** 用例 4：新增回填自增主键；不声明 useGeneratedKeys 时这里会是 null */
    @Test
    public void t4_insertBackfillsGeneratedKey() {
        Emp emp = Emp.of("E9100T", "主键回填验证", 3, "产品部", new BigDecimal("13500.00"));
        int affected = mapper.insert(emp);

        Assert.assertEquals("应影响 1 行", 1, affected);
        Assert.assertNotNull("未回填主键：请检查 <insert> 的 useGeneratedKeys/keyProperty", emp.getEmpId());
        Assert.assertTrue("回填的主键应大于种子数据最大主键", emp.getEmpId() > 16);

        Emp loaded = mapper.selectById(emp.getEmpId());
        Assert.assertNotNull(loaded);
        Assert.assertEquals("E9100T", loaded.getEmpNo());
        // 插入时默认 status=1 / del_flag=0，由 SQL 常量给定，防止调用方漏填
        Assert.assertEquals(Integer.valueOf(1), loaded.getStatus());
        Assert.assertEquals(Integer.valueOf(0), loaded.getDelFlag());
    }

    /** 用例 5：全字段更新会覆盖所有列，并把 version 自增 */
    @Test
    public void t5_fullUpdateOverwritesAllColumns() {
        Emp before = mapper.selectById(2);
        int versionBefore = before.getVersion();

        before.setEmpName("王芳-已改名");
        before.setPost("技术专家");
        before.setSalary(new BigDecimal("28000.00"));
        before.setHireDate(LocalDate.of(2016, 3, 16));
        int affected = mapper.update(before);
        Assert.assertEquals(1, affected);

        Emp after = mapper.selectById(2);
        Assert.assertEquals("王芳-已改名", after.getEmpName());
        Assert.assertEquals("技术专家", after.getPost());
        Assert.assertEquals(0, new BigDecimal("28000.00").compareTo(after.getSalary()));
        Assert.assertEquals(LocalDate.of(2016, 3, 16), after.getHireDate());
        Assert.assertEquals("version 应由 SQL 自增", versionBefore + 1, after.getVersion().intValue());
    }

    /** 用例 6：物理删除真的删行（与 MyBatis-Plus 的逻辑删除形成对照） */
    @Test
    public void t6_deleteByIdRemovesRowPhysically() {
        Emp emp = Emp.of("E9101D", "待删除", 4, "运维部", new BigDecimal("14000.00"));
        mapper.insert(emp);
        Integer id = emp.getEmpId();

        Assert.assertEquals(1, mapper.deleteById(id));
        Assert.assertNull("物理删除后应查不到", mapper.selectById(id));
        Assert.assertEquals("删除不存在的行应返回 0", 0, mapper.deleteById(id));
    }

    /** 用例 7：乐观锁字段由数据库维护，插入后 version 为 0 */
    @Test
    public void t7_newRowStartsWithZeroVersion() {
        Emp emp = Emp.of("E9102V", "版本初值", 5, "人事部", new BigDecimal("9500.00"));
        mapper.insert(emp);
        Assert.assertEquals(Integer.valueOf(0), mapper.selectById(emp.getEmpId()).getVersion());
    }

    /** 用例 8：Mapper 接口是通过 JDK 动态代理生成的，同一接口两次获取是不同实例 */
    @Test
    public void t8_mapperIsJdkDynamicProxy() {
        EmpMapper another = session.getMapper(EmpMapper.class);
        Assert.assertTrue("Mapper 应由 JDK 动态代理实现",
                java.lang.reflect.Proxy.isProxyClass(another.getClass()));
        Assert.assertNotSame("每次 getMapper 返回新的代理实例，但共享同一会话", mapper, another);
        // 两者背后是同一个会话，因此都能看到同一份数据
        Assert.assertEquals(mapper.selectById(1).getEmpNo(), another.selectById(1).getEmpNo());
    }
}
