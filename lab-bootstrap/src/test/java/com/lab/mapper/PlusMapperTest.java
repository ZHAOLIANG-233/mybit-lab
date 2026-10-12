package com.lab.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.common.entity.Emp;
import com.lab.plus.config.PlusConfigurationFactory;
import com.lab.plus.config.PlusSessionFactory;
import com.lab.support.PlusTestBase;
import org.apache.ibatis.session.SqlSession;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MyBatis-Plus 轨测试：通用 CRUD、条件构造器、分页插件、逻辑删除、乐观锁。
 *
 * <p>本组用例刻意区分"零 SQL 能力"与"仍需手写 SQL 的场景"：
 * 单表 CRUD 用 BaseMapper，多表聚合继续写 {@code @Select}。</p>
 */
public class PlusMapperTest extends PlusTestBase {

    /** 用例 1：通用 CRUD 四条主线（增、查、改、删）各走一遍 */
    @Test
    public void t1_genericCrudRoundTrip() {
        Emp emp = Emp.of("P7001A", "MP通用新增", 2, "测试部", new BigDecimal("13800.00"));
        Assert.assertEquals(1, mapper.insert(emp));
        Assert.assertNotNull("IdType.AUTO 应回填自增主键", emp.getEmpId());

        Emp loaded = mapper.selectById(emp.getEmpId());
        Assert.assertNotNull(loaded);
        Assert.assertEquals("P7001A", loaded.getEmpNo());

        loaded.setSalary(new BigDecimal("14800.00"));
        Assert.assertEquals(1, mapper.updateById(loaded));
        Assert.assertEquals(0, new BigDecimal("14800.00")
                .compareTo(mapper.selectById(emp.getEmpId()).getSalary()));

        // 物理删除：本用例在事务内，结束时统一回滚
        Assert.assertEquals(1, mapper.deleteById(emp.getEmpId()));
        Assert.assertNull(mapper.selectById(emp.getEmpId()));
    }

    /** 用例 2：@TableLogic 生效——查询自动追加 del_flag = 0 */
    @Test
    public void t2_tableLogicFiltersLogicallyDeletedRows() {
        // 直接按主键查，MP 仍会追加 del_flag = 0，因此查不到第 16 行
        Assert.assertNull("逻辑删除行不应被 selectById 命中", mapper.selectById(16));

        long alive = mapper.selectCount(null);
        Assert.assertEquals("自动过滤后应剩 15 行", 15L, alive);

        // 用 Wrapper 显式带条件时，逻辑删除条件同样会被追加，无法"绕过"
        long viaWrapper = mapper.selectCount(Wrappers.<Emp>lambdaQuery().eq(Emp::getDeptId, 1));
        List<Emp> devs = mapper.selectList(Wrappers.<Emp>lambdaQuery().eq(Emp::getDeptId, 1));
        Assert.assertEquals(devs.size(), (int) viaWrapper);
        Assert.assertTrue("结果中不应出现 del_flag=1 的记录",
                devs.stream().allMatch(e -> Integer.valueOf(0).equals(e.getDelFlag())));
    }

    /** 用例 3：deleteById 执行的是 UPDATE（逻辑删除），物理行仍在 */
    @Test
    public void t3_deleteByIdPerformsLogicalDelete() {
        Emp emp = Emp.of("P7003C", "MP逻辑删除", 3, "产品部", new BigDecimal("12800.00"));
        mapper.insert(emp);
        final Integer id = emp.getEmpId();
        Assert.assertNotNull(id);

        try {
            // 先提交插入：逻辑删除的效果要用【另一个连接】才能观察到，
            // 否则会读到本会话未提交的快照，得出"物理行消失了"的错误结论
            session.commit();

            Assert.assertEquals("deleteById 应影响 1 行", 1, mapper.deleteById(id));
            session.commit();
            Assert.assertNull("逻辑删除后 MP 查不到", mapper.selectById(id));

            try (SqlSession raw = com.lab.mybatis.config.MyBatisSessionFactory.openSession()) {
                Emp rawRow = raw.getMapper(com.lab.mybatis.mapper.EmpMapper.class).selectById(id);
                Assert.assertNotNull("逻辑删除不物理删行", rawRow);
                Assert.assertEquals("del_flag 应被置为 1", Integer.valueOf(1), rawRow.getDelFlag());
                Assert.assertEquals("业务状态 status 不应被逻辑删除影响",
                        Integer.valueOf(1), rawRow.getStatus());
            }
        } finally {
            // 本用例有提交，必须跨连接清理，保证数据基线零污染
            try (SqlSession cleanup = com.lab.mybatis.config.MyBatisSessionFactory.openSession()) {
                cleanup.getMapper(com.lab.mybatis.mapper.EmpMapper.class).deleteById(id);
                cleanup.commit();
            }
        }
    }

    /** 用例 4：LambdaQueryWrapper 多条件组合，结果与手写 SQL 口径一致 */
    @Test
    public void t4_lambdaQueryWrapperCombinesConditions() {
        LambdaQueryWrapper<Emp> wrapper = Wrappers.<Emp>lambdaQuery()
                .eq(Emp::getDeptId, 1)
                .eq(Emp::getStatus, 1)
                .ge(Emp::getSalary, new BigDecimal("20000"))
                .orderByAsc(Emp::getEmpId);

        List<Emp> list = mapper.selectList(wrapper);
        Assert.assertEquals("研发部在职且薪资 >= 20000 应为 2 人", 2, list.size());
        Assert.assertEquals(Arrays.asList("张伟", "王芳"),
                list.stream().map(Emp::getEmpName).collect(Collectors.toList()));
    }

    /** 用例 5：QueryWrapper 支持字符串列名，与 Lambda 写法结果一致 */
    @Test
    public void t5_queryWrapperMatchesLambdaWrapper() {
        List<Integer> byLambda = mapper.selectList(Wrappers.<Emp>lambdaQuery()
                        .eq(Emp::getDeptId, 2).orderByAsc(Emp::getEmpId))
                .stream().map(Emp::getEmpId).collect(Collectors.toList());

        List<Integer> byString = mapper.selectList(new QueryWrapper<Emp>()
                        .eq("dept_id", 2).orderByAsc("emp_id"))
                .stream().map(Emp::getEmpId).collect(Collectors.toList());

        Assert.assertEquals("两种条件构造器结果必须一致", byLambda, byString);
        Assert.assertFalse(byLambda.isEmpty());
    }

    /** 用例 6：Wrapper 的模糊查询也走预编译，注入串只会被当成普通文本 */
    @Test
    public void t6_wrapperLikeIsParameterized() {
        String evil = "' OR '1'='1";
        List<Emp> list = mapper.selectList(Wrappers.<Emp>lambdaQuery()
                .like(Emp::getEmpName, evil));
        Assert.assertTrue("注入串不应匹配到任何姓名", list.isEmpty());

        long normal = mapper.selectCount(Wrappers.<Emp>lambdaQuery().like(Emp::getEmpName, "张"));
        Assert.assertEquals(1L, normal);
    }

    /** 用例 7：分页插件生效，total / pages / records 三者自洽 */
    @Test
    public void t7_paginationPluginProducesCorrectTotals() {
        Page<Emp> page = mapper.selectPage(new Page<>(1, 4),
                Wrappers.<Emp>lambdaQuery().orderByAsc(Emp::getEmpId));

        Assert.assertEquals("总数应为 15", 15L, page.getTotal());
        Assert.assertEquals("4 页", 4L, page.getPages());
        Assert.assertEquals("当前页 4 条", 4, page.getRecords().size());
        Assert.assertTrue("应有下一页", page.hasNext());
        Assert.assertEquals(Integer.valueOf(1), page.getRecords().get(0).getEmpId());
    }

    /** 用例 8：分页插件静默失效的判据——拦截器数量必须为 1 且包含分页内部拦截器 */
    @Test
    public void t8_paginationPluginIsActuallyRegistered() {
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration =
                PlusConfigurationFactory.buildConfiguration();
        Assert.assertEquals("拦截器未被注册时，分页会退化成普通查询且 total 恒为 0",
                1, configuration.getInterceptors().size());

        com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor main =
                (com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor)
                        configuration.getInterceptors().get(0);
        boolean hasPagination = main.getInterceptors().stream()
                .anyMatch(i -> i instanceof
                        com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor);
        Assert.assertTrue("必须注册 PaginationInnerInterceptor", hasPagination);

        // 同时用真实查询交叉验证：total 不为 0 且 SQL 确实加了 LIMIT（记录数 <= 页大小）
        Page<Emp> page = mapper.selectPage(new Page<>(2, 3), null);
        Assert.assertEquals(15L, page.getTotal());
        Assert.assertTrue("当前页记录数不应超过页大小", page.getRecords().size() <= 3);
    }

    /** 用例 9：分页与条件组合，条件被带进 COUNT 与 LIMIT 两条 SQL */
    @Test
    public void t9_paginationWithConditions() {
        Page<Emp> page = mapper.selectPage(new Page<>(1, 2),
                Wrappers.<Emp>lambdaQuery().eq(Emp::getDeptId, 2).orderByAsc(Emp::getEmpId));

        // 测试部未逻辑删除 3 人：李娜、刘洋（在职）+ 何伟（离职但未删除）
        Assert.assertEquals(3L, page.getTotal());
        Assert.assertEquals(2L, page.getPages());
        Assert.assertEquals(2, page.getRecords().size());
        Assert.assertTrue(page.getRecords().stream()
                .allMatch(e -> Integer.valueOf(2).equals(e.getDeptId())));

        // 叠加 status 条件后总数必须变小，证明条件真的参与了 COUNT
        Page<Emp> onJob = mapper.selectPage(new Page<>(1, 2),
                Wrappers.<Emp>lambdaQuery().eq(Emp::getDeptId, 2).eq(Emp::getStatus, 1));
        Assert.assertEquals("测试部在职仅 2 人", 2L, onJob.getTotal());
        Assert.assertTrue(onJob.getRecords().stream()
                .allMatch(e -> Integer.valueOf(1).equals(e.getStatus())));
    }

    /** 用例 10：逻辑删除 + 唯一键的组合陷阱——被逻辑删除的工号仍占位 */
    @Test
    public void t10_logicallyDeletedRowStillOccupiesUniqueKey() {
        // emp_id=16 的工号 E2015016 已逻辑删除
        Emp reused = Emp.of("E2015016", "试图复用已删除工号", 1, "研发部",
                new BigDecimal("15000.00"));
        try {
            mapper.insert(reused);
            Assert.fail("唯一键仍然占位，插入应失败");
        } catch (org.apache.ibatis.exceptions.PersistenceException expected) {
            String message = String.valueOf(expected.getCause());
            Assert.assertTrue("应是唯一键冲突：" + message,
                    message.contains("Duplicate entry") || message.contains("uk_emp_no"));
        }
    }

    /** 用例 11：乐观锁——版本号不匹配时更新影响 0 行 */
    @Test
    public void t11_optimisticLockerRejectsStaleVersion() {
        Emp current = mapper.selectById(2);
        Assert.assertNotNull(current);
        int version = current.getVersion();

        Emp stale = new Emp();
        stale.setEmpId(2);
        stale.setEmpName("乐观锁-陈旧副本");
        stale.setVersion(version); // 与库里一致，应成功
        Assert.assertEquals(1, mapper.updateById(stale));
        Assert.assertEquals(version + 1,
                mapper.selectById(2).getVersion().intValue());

        Emp staler = new Emp();
        staler.setEmpId(2);
        staler.setEmpName("乐观锁-更陈旧副本");
        staler.setVersion(version); // 版本已过期，应失败
        Assert.assertEquals("版本过期时不应更新任何行", 0, mapper.updateById(staler));
        Assert.assertEquals("姓名不应被过期副本覆盖",
                "乐观锁-陈旧副本", mapper.selectById(2).getEmpName());
    }

    /** 用例 12：LambdaUpdateWrapper 批量条件更新 */
    @Test
    public void t12_lambdaUpdateWrapperUpdatesByCondition() {
        LambdaUpdateWrapper<Emp> wrapper = Wrappers.<Emp>lambdaUpdate()
                .eq(Emp::getDeptId, 6)
                .eq(Emp::getStatus, 1)
                .set(Emp::getPost, "财务专员");

        int affected = mapper.update(null, wrapper);
        Assert.assertEquals("财务部在职 2 人应被更新", 2, affected);
        Assert.assertTrue(mapper.selectList(Wrappers.<Emp>lambdaQuery().eq(Emp::getDeptId, 6))
                .stream().allMatch(e -> "财务专员".equals(e.getPost())));
    }

    /** 用例 13：selectBatchIds 与手写 IN 查询结果一致 */
    @Test
    public void t13_selectBatchIdsMatchesManualIn() {
        List<Integer> ids = Arrays.asList(1, 3, 5, 16);
        List<Emp> batch = mapper.selectBatchIds(ids);
        Assert.assertEquals("16 号已逻辑删除，应只返回 3 条", 3, batch.size());

        List<Integer> returned = batch.stream().map(Emp::getEmpId).collect(Collectors.toList());
        Assert.assertEquals(Arrays.asList(1, 3, 5), returned);
        Assert.assertFalse("逻辑删除行不应出现在批量查询里", returned.contains(16));
    }

    /** 用例 14：手写 @Select 与 Wrapper 混用——聚合类查询仍以 SQL 为主 */
    @Test
    public void t14_handWrittenSqlCoexistsWithWrapper() {
        List<Map<String, Object>> stats = mapper.selectDeptStat();
        Assert.assertEquals("应返回 6 个部门", 6, stats.size());
        Map<String, Object> first = stats.get(0);
        Assert.assertEquals("研发部", first.get("deptName"));
        Assert.assertEquals(2L, ((Number) first.get("empCount")).longValue());

        long onJob = mapper.countOnJobByDept(1);
        Assert.assertEquals(2L, onJob);
        Assert.assertTrue(mapper.countByMinSalary(new BigDecimal("30000")) >= 1);
    }

    /** 用例 15：Page 对象的边界——页大小与页码越界不会抛异常 */
    @Test
    public void t15_pageBoundariesAreSafe() {
        Page<Emp> beyond = mapper.selectPage(new Page<>(99, 10), null);
        Assert.assertEquals(15L, beyond.getTotal());
        Assert.assertTrue("越界页应返回空记录而不是异常", beyond.getRecords().isEmpty());

        Page<Emp> zeroSize = mapper.selectPage(new Page<>(1, 0), null);
        // 页大小为 0 时 MP 会退化为"不分页"，此时记录数应等于总数
        Assert.assertEquals(15L, zeroSize.getTotal());
        Assert.assertTrue(zeroSize.getRecords().size() <= 15);

        IPage<Emp> asInterface = beyond;
        Assert.assertEquals(2L, asInterface.getPages());
    }

    /** 用例 16：逻辑删除条件与手写 SQL 的关系——手写 SQL 必须自己带条件 */
    @Test
    public void t16_handWrittenSqlMustCarryDelFlagItself() {
        long withFlag = mapper.countByMinSalary(new BigDecimal("0"));
        // 该手写 SQL 自带 del_flag = 0，因此与 MP 自动过滤的口径一致
        Assert.assertEquals(15L, withFlag);

        // 用纯 MyBatis 复核：物理上确实是 16 行
        try (SqlSession raw = com.lab.mybatis.config.MyBatisSessionFactory.openSession()) {
            Assert.assertEquals(16,
                    raw.getMapper(com.lab.mybatis.mapper.EmpMapper.class).selectAll().size());
        }
    }

    /** 用例 17：两轨共用同一份实体与注解，字段名不会漂移 */
    @Test
    public void t17_entityMetadataIsSharedAcrossTracks() {
        com.baomidou.mybatisplus.core.metadata.TableInfo info =
                com.baomidou.mybatisplus.core.metadata.TableInfoHelper
                        .getTableInfo(com.lab.common.entity.Emp.class);
        Assert.assertNotNull(info);
        Assert.assertEquals("emp", info.getTableName());
        Assert.assertEquals("emp_id", info.getKeyColumn());
        Assert.assertTrue("逻辑删除字段应被识别为 del_flag",
                info.isWithLogicDelete());
        Assert.assertEquals("del_flag", info.getLogicDeleteFieldInfo().getColumn());
        Assert.assertEquals("version 应被识别为乐观锁字段", "version",
                info.getVersionFieldInfo().getColumn());
    }

    /** 用例 18：逻辑删除值来自 @TableLogic 的声明，可直接断言 */
    @Test
    public void t18_logicDeleteValuesComeFromAnnotation() {
        com.baomidou.mybatisplus.core.metadata.TableFieldInfo delFlagField =
                com.baomidou.mybatisplus.core.metadata.TableInfoHelper
                        .getTableInfo(Emp.class).getFieldList().stream()
                        .filter(com.baomidou.mybatisplus.core.metadata.TableFieldInfo::isLogicDelete)
                        .findFirst().orElse(null);
        Assert.assertNotNull("应存在逻辑删除字段", delFlagField);
        Assert.assertEquals("del_flag", delFlagField.getColumn());
        Assert.assertEquals("未删除值必须是 0", "0", delFlagField.getLogicNotDeleteValue());
        Assert.assertEquals("已删除值必须是 1", "1", delFlagField.getLogicDeleteValue());
    }

    /** 用例 19：hire_date 映射无损；同一轨的工厂是单例 */
    @Test
    public void t19_hireDateMappingIsLosslessAndFactoryIsSingleton() {
        Emp emp = mapper.selectById(1);
        Assert.assertEquals(LocalDate.of(2015, 7, 1), emp.getHireDate());
        Assert.assertSame("工厂应为应用级单例",
                PlusSessionFactory.getFactory(), PlusSessionFactory.getFactory());
    }
}
