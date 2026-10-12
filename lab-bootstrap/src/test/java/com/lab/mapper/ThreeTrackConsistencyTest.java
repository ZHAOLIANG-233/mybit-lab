package com.lab.mapper;

import com.lab.common.entity.Emp;
import com.lab.mybatis.config.MyBatisSessionFactory;
import com.lab.mybatis.mapper.EmpAnnotationMapper;
import com.lab.mybatis.mapper.EmpMapper;
import com.lab.plus.config.PlusSessionFactory;
import com.lab.plus.mapper.EmpPlusMapper;
import org.apache.ibatis.session.SqlSession;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 三轨一致性测试：XML 轨、注解轨、MyBatis-Plus 轨对同一批数据的查询结果必须一致。
 *
 * <p>三条轨各自独立实现同一语义，因此"结果是否一致"必须被断言，而不能靠人工比对。</p>
 *
 * <p>同时把<b>唯一一处刻意的不一致</b>钉死：{@code emp_id=16} 是逻辑删除行，
 * 纯 MyBatis 两轨查得到（不自动过滤），MyBatis-Plus 轨查不到（{@code @TableLogic}
 * 自动追加 {@code del_flag = 0}）。这不是缺陷，而是两条路线的语义差异，
 * 写成测试可以防止它日后被误判为 bug 而"修错"。</p>
 */
public class ThreeTrackConsistencyTest {

    /** 参与逐字段比对的字段数（不含主键与审计时间） */
    private static final int COMPARED_FIELDS = 13;

    /** 用例 1：同一主键在三轨下逐字段一致 */
    @Test
    public void t1_sameRowIsIdenticalAcrossThreeTracks() {
        try (SqlSession plain = MyBatisSessionFactory.openSession();
             SqlSession plus = PlusSessionFactory.openSession()) {

            Emp byXml = plain.getMapper(EmpMapper.class).selectById(5);
            Emp byAnnotation = plain.getMapper(EmpAnnotationMapper.class).selectById(5);
            Emp byPlus = plus.getMapper(EmpPlusMapper.class).selectById(5);

            Assert.assertNotNull(byXml);
            Assert.assertNotNull(byAnnotation);
            Assert.assertNotNull(byPlus);

            assertSameBusinessFields(byXml, "XML", byAnnotation, "注解");
            assertSameBusinessFields(byXml, "XML", byPlus, "MyBatis-Plus");
        }
    }

    /** 用例 2：批量比对——在职员工集合在三轨下主键序列完全一致 */
    @Test
    public void t2_aliveOnJobCollectionsMatchAcrossTracks() {
        try (SqlSession plain = MyBatisSessionFactory.openSession();
             SqlSession plus = PlusSessionFactory.openSession()) {

            List<Integer> byXml = plain.getMapper(EmpMapper.class).selectAll().stream()
                    .filter(e -> Integer.valueOf(0).equals(e.getDelFlag())
                            && Integer.valueOf(1).equals(e.getStatus()))
                    .map(Emp::getEmpId).collect(Collectors.toList());

            List<Integer> byAnnotation = plain.getMapper(EmpAnnotationMapper.class)
                    .selectAliveOnJob().stream().map(Emp::getEmpId).collect(Collectors.toList());

            List<Integer> byPlus = plus.getMapper(EmpPlusMapper.class)
                    .selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Emp>()
                            .eq(Emp::getStatus, 1)
                            .orderByAsc(Emp::getEmpId))
                    .stream().map(Emp::getEmpId).collect(Collectors.toList());

            Assert.assertEquals("在职未删除应为 12 人", 12, byXml.size());
            Assert.assertEquals("XML 轨与注解轨结果必须一致", byXml, byAnnotation);
            Assert.assertEquals("XML 轨与 MyBatis-Plus 轨结果必须一致", byXml, byPlus);

            // 注解轨的 countAlive 统计的是"未逻辑删除"（含离职），应为 15
            Assert.assertEquals("未逻辑删除总行数应为 15",
                    15L, plain.getMapper(EmpAnnotationMapper.class).countAlive());
        }
    }

    /** 用例 3：薪资区间查询在三轨下返回同一批主键 */
    @Test
    public void t3_salaryRangeIsConsistentAcrossTracks() {
        BigDecimal min = new BigDecimal("12000");
        BigDecimal max = new BigDecimal("26000");

        try (SqlSession plain = MyBatisSessionFactory.openSession();
             SqlSession plus = PlusSessionFactory.openSession()) {

            List<Integer> byXml = plain.getMapper(EmpMapper.class)
                    .selectBySalaryRange(min, max).stream()
                    .map(Emp::getEmpId).collect(Collectors.toList());

            List<Integer> byAnnotation = plain.getMapper(EmpAnnotationMapper.class)
                    .selectBySalaryRange(min, max).stream()
                    .map(Emp::getEmpId).collect(Collectors.toList());

            List<Integer> byPlus = plus.getMapper(EmpPlusMapper.class)
                    .selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Emp>()
                            .between(Emp::getSalary, min, max)
                            .orderByAsc(Emp::getSalary))
                    .stream().map(Emp::getEmpId).collect(Collectors.toList());

            Assert.assertFalse("区间内应有数据", byXml.isEmpty());
            Assert.assertEquals("XML 轨与注解轨应一致", byXml, byAnnotation);
            // MP 轨按薪资升序、XML 轨也按薪资升序，因此顺序可直接比较
            Assert.assertEquals("XML 轨与 MyBatis-Plus 轨应一致", byXml, byPlus);
        }
    }

    /** 用例 4：把逻辑删除的口径差异固定下来——这是三轨唯一不一致的地方 */
    @Test
    public void t4_logicalDeleteDifferenceIsPinnedDown() {
        final int logicallyDeletedId = 16;

        try (SqlSession plain = MyBatisSessionFactory.openSession();
             SqlSession plus = PlusSessionFactory.openSession()) {

            Emp byXml = plain.getMapper(EmpMapper.class).selectById(logicallyDeletedId);
            Emp byAnnotation = plain.getMapper(EmpAnnotationMapper.class)
                    .selectById(logicallyDeletedId);
            Emp byPlus = plus.getMapper(EmpPlusMapper.class).selectById(logicallyDeletedId);

            Assert.assertNotNull("纯 MyBatis（XML）应能看到已逻辑删除的行", byXml);
            Assert.assertNotNull("纯 MyBatis（注解）应能看到已逻辑删除的行", byAnnotation);
            Assert.assertNull("MyBatis-Plus 应自动过滤已逻辑删除的行", byPlus);
            Assert.assertEquals("该行的 del_flag 应为 1",
                    Integer.valueOf(1), byXml.getDelFlag());

            // 总数口径：XML 轨 16 行，注解/MP 轨 15 行
            long xmlTotal = plain.getMapper(EmpMapper.class).selectAll().size();
            long plusTotal = plus.getMapper(EmpPlusMapper.class).selectCount(null);
            Assert.assertEquals("原生轨物理行数", 16L, xmlTotal);
            Assert.assertEquals("MP 轨自动过滤后的行数", 15L, plusTotal);
        }
    }

    /** 逐字段比对业务字段，失败时给出可比对的字段名 */
    private static void assertSameBusinessFields(Emp left, String leftTrack,
                                                  Emp right, String rightTrack) {
        int compared = 0;
        assertField(left.getEmpNo(), right.getEmpNo(), "empNo", leftTrack, rightTrack);
        compared++;
        assertField(left.getEmpName(), right.getEmpName(), "empName", leftTrack, rightTrack);
        compared++;
        assertField(left.getGender(), right.getGender(), "gender", leftTrack, rightTrack);
        compared++;
        assertField(left.getBirthDate(), right.getBirthDate(), "birthDate", leftTrack, rightTrack);
        compared++;
        assertField(left.getEmail(), right.getEmail(), "email", leftTrack, rightTrack);
        compared++;
        assertField(left.getPhone(), right.getPhone(), "phone", leftTrack, rightTrack);
        compared++;
        assertField(left.getDeptId(), right.getDeptId(), "deptId", leftTrack, rightTrack);
        compared++;
        assertField(left.getDept(), right.getDept(), "dept", leftTrack, rightTrack);
        compared++;
        assertField(left.getPost(), right.getPost(), "post", leftTrack, rightTrack);
        compared++;
        Assert.assertEquals("salary", 0, left.getSalary().compareTo(right.getSalary()));
        compared++;
        assertField(left.getHireDate(), right.getHireDate(), "hireDate", leftTrack, rightTrack);
        compared++;
        assertField(left.getStatus(), right.getStatus(), "status", leftTrack, rightTrack);
        compared++;
        assertField(left.getDelFlag(), right.getDelFlag(), "delFlag", leftTrack, rightTrack);
        compared++;
        Assert.assertEquals("比对字段数应覆盖全部业务字段", COMPARED_FIELDS, compared);
    }

    private static void assertField(Object left, Object right, String field,
                                    String leftTrack, String rightTrack) {
        Assert.assertEquals(leftTrack + " 轨与 " + rightTrack + " 轨字段不一致：" + field,
                left, right);
    }
}
