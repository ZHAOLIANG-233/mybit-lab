package com.lab.mapper;

import com.lab.common.dto.EmpQuery;
import com.lab.common.dto.PageQuery;
import com.lab.common.dto.PageResult;
import com.lab.common.entity.Emp;
import com.lab.mybatis.service.EmpService;
import com.lab.mybatis.service.impl.EmpServiceImpl;
import com.lab.support.TestBase;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 自研分页测试（COUNT + LIMIT 两条 SQL）。
 *
 * <p>为什么不用现成插件？因为"分页是怎么算出来的"必须可见：
 * 总数、页数、当前页数据三者可以直接对照。
 * 本组用例把这三者都断言下来，包括最容易出错的两个边界——
 * 页大小上限与"页大小不能为 0"。</p>
 */
public class PaginationTest extends TestBase {

    private final EmpService service = new EmpServiceImpl();

    /** 用例 1：第一页的基本口径——总数 15、页数 4、当前页 4 条 */
    @Test
    public void t1_firstPageHasCorrectTotals() {
        PageResult<Emp> page = service.page(new EmpQuery(), new PageQuery(1, 4));

        Assert.assertEquals("未删除总数应为 15", 15L, page.getTotal());
        Assert.assertEquals("15 / 4 向上取整应为 4 页", 4, page.getPages());
        Assert.assertEquals("第一页应有 4 条", 4, page.getRecords().size());
        Assert.assertEquals(Integer.valueOf(1), page.getRecords().get(0).getEmpId());
        Assert.assertTrue("第一页之后应还有下一页", page.isHasNext());
    }

    /** 用例 2：最后一页只剩余数，不能报错也不能多给 */
    @Test
    public void t2_lastPageReturnsRemainder() {
        PageResult<Emp> page = service.page(new EmpQuery(), new PageQuery(4, 4));
        Assert.assertEquals(15L, page.getTotal());
        Assert.assertEquals("第 4 页只剩 15 - 3*4 = 3 条", 3, page.getRecords().size());
        Assert.assertFalse("最后一页不应再有下一页", page.isHasNext());
    }

    /** 用例 3：页大小恰好整除时页数不应多算一页 */
    @Test
    public void t3_exactDivisionDoesNotAddExtraPage() {
        PageResult<Emp> page = service.page(new EmpQuery(), new PageQuery(1, 5));
        Assert.assertEquals("15 / 5 = 3 页", 3, page.getPages());
        Assert.assertTrue("共 3 页，第 1 页之后还有数据", page.isHasNext());

        PageResult<Emp> last = service.page(new EmpQuery(), new PageQuery(3, 5));
        Assert.assertFalse("第 3 页是最后一页，不应多算第 4 页", last.isHasNext());
        Assert.assertEquals(5, last.getRecords().size());

        // 关键点：若页数是"除法 + 1"的实现，15/5 会算成 4 页，这里就会失败
        Assert.assertEquals("总页数不能多算", 3, last.getPages());
    }

    /** 用例 4：页码越界返回空列表，但总数与页数仍然正确 */
    @Test
    public void t4_outOfRangePageReturnsEmptyRecords() {
        PageResult<Emp> page = service.page(new EmpQuery(), new PageQuery(99, 10));
        Assert.assertEquals("总数与页码无关，仍应为 15", 15L, page.getTotal());
        Assert.assertEquals(2, page.getPages());
        Assert.assertTrue("越界页应返回空列表", page.getRecords().isEmpty());
    }

    /** 用例 5：页大小 0 / 负数 / null 被归一化为默认值，绝不退化成全表查询 */
    @Test
    public void t5_invalidPageSizeFallsBackToDefault() {
        PageQuery zero = new PageQuery(1, 0);
        Assert.assertEquals("0 应回落为默认页大小", PageQuery.DEFAULT_PAGE_SIZE, zero.getSafePageSize());
        Assert.assertEquals(0, zero.getOffset());

        PageQuery negative = new PageQuery(2, -50);
        Assert.assertEquals(PageQuery.DEFAULT_PAGE_SIZE, negative.getSafePageSize());
        Assert.assertEquals(PageQuery.DEFAULT_PAGE_SIZE, negative.getOffset());

        PageQuery nullSize = new PageQuery(1, null);
        Assert.assertEquals(PageQuery.DEFAULT_PAGE_SIZE, nullSize.getSafePageSize());

        // 归一化之后服务层不会抛异常，也不会把整表拉出来
        PageResult<Emp> page = service.page(new EmpQuery(), zero);
        Assert.assertEquals(PageQuery.DEFAULT_PAGE_SIZE, page.getPageSize());
        Assert.assertTrue(page.getRecords().size() <= PageQuery.DEFAULT_PAGE_SIZE);
    }

    /** 用例 6：页大小超过上限（100）被截断，防止"一次查全表" */
    @Test
    public void t6_pageSizeAboveCapIsTruncated() {
        PageQuery huge = new PageQuery(1, 100000);
        Assert.assertEquals("页大小上限必须生效", PageQuery.MAX_PAGE_SIZE, huge.getSafePageSize());

        PageQuery boundary = new PageQuery(1, PageQuery.MAX_PAGE_SIZE);
        Assert.assertEquals(PageQuery.MAX_PAGE_SIZE, boundary.getSafePageSize());
    }

    /** 用例 7：分页只取"当前页那部分"，不是先查全表再在内存里截断 */
    @Test
    public void t7_paginationIsPushedDownToSql() {
        EmpQuery q = new EmpQuery();
        q.setOrderBy("salary");
        q.setAsc(Boolean.TRUE);

        PageQuery firstPage = new PageQuery(1, 3);
        List<Emp> records = mapper.selectPage(q, firstPage);

        Assert.assertEquals("数据库端只应返回 3 行", 3, records.size());
        Assert.assertEquals("按薪资升序的第一页应是 3 条最低薪", 3, records.size());
        List<BigDecimal> salaries = records.stream().map(Emp::getSalary).collect(Collectors.toList());
        for (int i = 1; i < salaries.size(); i++) {
            Assert.assertTrue("结果应已按薪资升序",
                    salaries.get(i - 1).compareTo(salaries.get(i)) <= 0);
        }

        // 与第二页无交集，进一步证明 LIMIT 真的下推到了数据库
        List<Integer> firstIds = records.stream().map(Emp::getEmpId).collect(Collectors.toList());
        List<Integer> secondIds = mapper.selectPage(q, new PageQuery(2, 3))
                .stream().map(Emp::getEmpId).collect(Collectors.toList());
        Assert.assertTrue("两页主键不应有交集",
                firstIds.stream().noneMatch(secondIds::contains));
    }

    /** 用例 8：分页与条件组合，总数随条件变化而不是恒为 15 */
    @Test
    public void t8_paginationRespectsConditions() {
        EmpQuery q = new EmpQuery();
        q.setDeptId(1);
        q.setStatus(1);
        PageResult<Emp> page = service.page(q, new PageQuery(1, 10));

        // 研发部未删除且在职：张伟、王芳（许晴已离职，邓涛已逻辑删除）
        Assert.assertEquals(2L, page.getTotal());
        Assert.assertEquals(1, page.getPages());
        Assert.assertEquals(2, page.getRecords().size());
        Assert.assertTrue(page.getRecords().stream()
                .allMatch(e -> Integer.valueOf(1).equals(e.getDeptId())
                        && Integer.valueOf(1).equals(e.getStatus())));

        // 去掉 status 条件后，同一部门应多出"离职但未删除"的许晴，说明条件确实生效
        EmpQuery withoutStatus = new EmpQuery();
        withoutStatus.setDeptId(1);
        Assert.assertEquals("不带 status 条件时应为 3 条",
                3L, service.page(withoutStatus, new PageQuery(1, 10)).getTotal());
    }

    /** 用例 9：offset 计算正确，避免"翻页翻错位" */
    @Test
    public void t9_offsetIsComputedCorrectly() {
        Assert.assertEquals(0, new PageQuery(1, 10).getOffset());
        Assert.assertEquals(10, new PageQuery(2, 10).getOffset());
        Assert.assertEquals(45, new PageQuery(4, 15).getOffset());
        // 页码 < 1 时按第 1 页处理
        Assert.assertEquals(0, new PageQuery(0, 10).getOffset());
        Assert.assertEquals(0, new PageQuery(-3, 10).getOffset());
    }
}
