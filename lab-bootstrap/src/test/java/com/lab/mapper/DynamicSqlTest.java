package com.lab.mapper;

import com.lab.common.dto.EmpQuery;
import com.lab.common.entity.Emp;
import com.lab.support.TestBase;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 动态 SQL 测试：覆盖 &lt;if&gt; / &lt;where&gt; / &lt;trim&gt; / &lt;set&gt; /
 * &lt;choose&gt; / &lt;foreach&gt; 六类标签。
 *
 * <p>断言的对象不只是"有没有报错"，而是<b>行数与字段值</b>：
 * 动态 SQL 最典型的缺陷是"条件没拼上"，此时 SQL 合法、不报错、结果偏多，
 * 只有把期望行数写死才能发现。</p>
 */
public class DynamicSqlTest extends TestBase {

    /** 用例 1：多条件同时生效（仅未删除 + 部门 + 薪资区间 + 在职） */
    @Test
    public void t1_multiConditionCombines() {
        EmpQuery q = new EmpQuery();
        q.setDeptId(1);
        q.setMinSalary(new BigDecimal("20000"));
        q.setStatus(1);

        List<Emp> list = mapper.selectByCondition(q);
        List<String> names = list.stream().map(Emp::getEmpName).collect(Collectors.toList());

        // 研发部在职且薪资 >= 20000 的只有 张伟(32000) 与 王芳(26000)
        Assert.assertEquals(2, list.size());
        Assert.assertTrue(names.contains("张伟"));
        Assert.assertTrue(names.contains("王芳"));
        // 该员工已被逻辑删除，必须被 onlyAlive 默认条件排除
        Assert.assertFalse("已逻辑删除的记录不应出现", names.contains("邓涛"));
    }

    /** 用例 2：只给姓名条件时走模糊匹配 */
    @Test
    public void t2_singleConditionFuzzyMatch() {
        EmpQuery q = new EmpQuery();
        q.setEmpName("张");
        List<Emp> list = mapper.selectByCondition(q);
        Assert.assertEquals(1, list.size());
        Assert.assertEquals("张伟", list.get(0).getEmpName());
    }

    /** 用例 3：空姓名串不参与拼接（`!= ''` 判空的意义所在） */
    @Test
    public void t3_emptyStringConditionIsIgnored() {
        EmpQuery q = new EmpQuery();
        q.setEmpName("");
        List<Emp> list = mapper.selectByCondition(q);
        Assert.assertEquals("空姓名应退化为\"查未删除的全部\"", 15, list.size());
    }

    /** 用例 4：所有条件都为 null 时只有一个 del_flag 条件，不会产生语法错误 */
    @Test
    public void t4_allNullConditionsStillProducesValidSql() {
        EmpQuery q = new EmpQuery();
        List<Emp> list = mapper.selectByCondition(q);
        Assert.assertEquals(15, list.size());
        Assert.assertEquals(list.size(), mapper.countByCondition(q));
    }

    /** 用例 5：&lt;where&gt; 与 &lt;trim&gt; 两种容器在相同条件下结果完全一致 */
    @Test
    public void t5_whereAndTrimAreEquivalent() {
        EmpQuery q = new EmpQuery();
        q.setDeptId(2);
        q.setMinSalary(new BigDecimal("13000"));
        q.setMaxSalary(new BigDecimal("20000"));

        List<Emp> byWhere = mapper.selectByCondition(q);
        List<Emp> byTrim = mapper.selectByConditionWithTrim(q);

        Assert.assertEquals("两种容器行数必须一致", byWhere.size(), byTrim.size());
        Assert.assertEquals("两种容器结果集必须一致",
                byWhere.stream().map(Emp::getEmpId).collect(Collectors.toList()),
                byTrim.stream().map(Emp::getEmpId).collect(Collectors.toList()));
        Assert.assertEquals(2, byWhere.size()); // 李娜 14000、刘洋 18500
    }

    /** 用例 6：&lt;choose&gt; 三级分支——姓名优先 */
    @Test
    public void t6_choosePrefersName() {
        List<Emp> list = mapper.selectByPriority("王芳", 3);
        Assert.assertEquals("姓名分支命中时应只返回该员工", 1, list.size());
        Assert.assertEquals("王芳", list.get(0).getEmpName());
    }

    /** 用例 7：&lt;choose&gt; 第二级——部门分支 */
    @Test
    public void t7_chooseFallsBackToDept() {
        List<Emp> list = mapper.selectByPriority(null, 4);
        Assert.assertEquals("运维部在职 2 人", 2, list.size());
        Assert.assertTrue(list.stream().allMatch(e -> Integer.valueOf(4).equals(e.getDeptId())));
    }

    /** 用例 8：&lt;otherwise&gt; 兜底，且外层 LIMIT 5 生效 */
    @Test
    public void t8_otherwiseBranchAppliesLimit() {
        List<Emp> list = mapper.selectByPriority(null, null);
        Assert.assertEquals("兜底分支最多返回 5 条", 5, list.size());
        Assert.assertTrue("兜底分支必须是未删除的在职员工",
                list.stream().allMatch(e -> Integer.valueOf(1).equals(e.getStatus())
                        && Integer.valueOf(0).equals(e.getDelFlag())));
    }

    /** 用例 9：&lt;set&gt; 只更新传入的非空字段，其余字段保持不变 */
    @Test
    public void t9_setUpdatesOnlyNonNullFields() {
        Emp before = mapper.selectById(3);
        String originalName = before.getEmpName();
        String originalPost = before.getPost();
        BigDecimal originalSalary = before.getSalary();
        int versionBefore = before.getVersion();

        Emp patch = new Emp();
        patch.setEmpId(3);
        patch.setSalary(new BigDecimal("15500.00")); // 只改薪资
        Assert.assertEquals(1, mapper.updateDynamic(patch));

        Emp after = mapper.selectById(3);
        Assert.assertEquals("未传入的字段不应被改动", originalName, after.getEmpName());
        Assert.assertEquals("未传入的字段不应被改动", originalPost, after.getPost());
        Assert.assertEquals(0, new BigDecimal("15500.00").compareTo(after.getSalary()));
        Assert.assertNotEquals("原薪资应已被覆盖", 0, originalSalary.compareTo(after.getSalary()));
        Assert.assertEquals("version 仍应自增", versionBefore + 1, after.getVersion().intValue());
    }

    /** 用例 10：&lt;foreach&gt; 批量插入，逐行回填主键并全部可查 */
    @Test
    public void t10_foreachBatchInsertBackfillsEveryKey() {
        List<Emp> batch = new ArrayList<>();
        String[] nos = {"E9201F", "E9202F", "E9203F", "E9204F", "E9205F"};
        String[] names = {"批量甲", "批量乙", "批量丙", "批量丁", "批量戊"};
        for (int i = 0; i < nos.length; i++) {
            Emp e = Emp.of(nos[i], names[i], 2, "测试部",
                    new BigDecimal(10000 + i * 1000 + ".00"));
            e.setGender(i % 2 == 0 ? "男" : "女");
            batch.add(e);
        }

        Assert.assertEquals("应为 5 行", 5, mapper.insertBatch(batch));
        for (Emp e : batch) {
            Assert.assertNotNull("批量插入也应逐行回填主键：" + e.getEmpNo(), e.getEmpId());
            Assert.assertNotNull(mapper.selectById(e.getEmpId()));
        }

        List<Integer> ids = batch.stream().map(Emp::getEmpId).collect(Collectors.toList());
        List<Emp> loaded = mapper.selectByCondition(dept2Query());
        Assert.assertTrue("批量插入的数据应能按条件查到",
                loaded.stream().map(Emp::getEmpId).collect(Collectors.toList()).containsAll(ids));
    }

    /** 用例 11：&lt;foreach&gt; 批量删除，IN 列表展开正确 */
    @Test
    public void t11_foreachBatchDeleteRemovesExactlyThoseRows() {
        List<Emp> batch = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            batch.add(Emp.of("E930" + i + "X", "批量删" + i, 3, "产品部", new BigDecimal("11000.00")));
        }
        mapper.insertBatch(batch);
        List<Integer> ids = batch.stream().map(Emp::getEmpId).collect(Collectors.toList());
        System.out.println("[DYNAMIC] 批量删除目标 ids=" + ids);

        Assert.assertEquals("IN 列表应精确命中 3 行", 3, mapper.deleteBatch(ids));
        for (Integer id : ids) {
            Assert.assertNull(mapper.selectById(id));
        }
        Assert.assertEquals("重复删除应返回 0", 0, mapper.deleteBatch(ids));
    }

    /** 用例 12：动态列名走白名单——非法排序字段被回落为 emp_id，注入串无效 */
    @Test
    public void t12_orderByIsRestrictedByWhitelist() {
        EmpQuery q = new EmpQuery();
        q.setOrderBy("emp_id; DROP TABLE emp; --");
        Assert.assertEquals("非法排序列名必须回落到 emp_id", "emp_id", q.getSafeOrderBy());

        List<Emp> list = mapper.selectByCondition(q);
        Assert.assertEquals("回落之后查询依旧正常", 15, list.size());
        Assert.assertEquals(Integer.valueOf(1), list.get(0).getEmpId());

        // 用真实客户需求验证"合法值按预期排序"
        EmpQuery bySalary = new EmpQuery();
        bySalary.setOrderBy("salary");
        bySalary.setAsc(Boolean.FALSE);
        List<Emp> desc = mapper.selectByCondition(bySalary);
        Assert.assertEquals(0, new BigDecimal("32000.00").compareTo(desc.get(0).getSalary()));
    }

    /** 用例 13：BETWEEN 为闭区间，边界值本身必须命中 */
    @Test
    public void t13_salaryRangeIsClosedInterval() {
        List<Emp> list = mapper.selectBySalaryRange(new BigDecimal("14000"), new BigDecimal("21000"));
        List<BigDecimal> salaries = list.stream().map(Emp::getSalary).collect(Collectors.toList());
        Assert.assertTrue("应包含下边界 14000", salaries.contains(new BigDecimal("14000.00")));
        Assert.assertFalse("上边界之外不应出现", salaries.contains(new BigDecimal("21000.01")));
        Assert.assertTrue("薪资应落在闭区间内", salaries.stream()
                .allMatch(s -> s.compareTo(new BigDecimal("14000")) >= 0
                        && s.compareTo(new BigDecimal("21000")) <= 0));
    }

    /** 用例 14：日期条件使用 LocalDate，边界当天包含在内 */
    @Test
    public void t14_hireDateRangeUsesLocalDate() {
        EmpQuery q = new EmpQuery();
        q.setHireDateFrom(LocalDate.of(2020, 1, 1));
        q.setHireDateTo(LocalDate.of(2021, 12, 31));
        List<Emp> list = mapper.selectByCondition(q);
        Assert.assertTrue("结果应全部落在区间内", list.stream().allMatch(e ->
                !e.getHireDate().isBefore(LocalDate.of(2020, 1, 1))
                        && !e.getHireDate().isAfter(LocalDate.of(2021, 12, 31))));
        Assert.assertTrue("应包含 2021-01-04 入职的马丽（未删除口径下）", list.size() >= 3);
    }

    /** 用例 15：结果为 Map 时列别名即 key，便于做分组统计 */
    @Test
    public void t15_mapResultUsesColumnAliasAsKey() {
        List<Map<String, Object>> rows = mapper.countGroupByPost();
        Assert.assertFalse(rows.isEmpty());
        Map<String, Object> first = rows.get(0);
        Assert.assertTrue("应包含 post 列", first.containsKey("post"));
        Assert.assertTrue("应包含 cnt 列", first.containsKey("cnt"));
        long sum = rows.stream().mapToLong(r -> ((Number) r.get("cnt")).longValue()).sum();
        Assert.assertEquals("分组计数之和应等于未删除总行数", 15L, sum);
    }

    /** 用例 16：多参数方法必须显式 @Param，否则 #{q} 会解析失败 */
    @Test
    public void t16_multipleParametersAreNamedByParam() {
        // 若接口缺少 @Param，这里会抛 BindingException: Parameter 'q' not found
        EmpQuery q = new EmpQuery();
        q.setStatus(1);
        List<Emp> list = mapper.selectByCondition(q);
        Assert.assertEquals("在职未删除共 12 人", 12, list.size());
        // 同时验证"列表"与"计数"两个多参数/单参数入口共用同一条件片段
        Assert.assertEquals("计数口径应与列表一致", 12L, mapper.countByCondition(q));
    }

    private EmpQuery dept2Query() {
        EmpQuery q = new EmpQuery();
        q.setDeptId(2);
        return q;
    }
}
