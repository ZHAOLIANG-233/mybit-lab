package com.lab.plus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.common.entity.Emp;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 员工 Mapper（MyBatis-Plus 轨）。
 *
 * <p>只继承 {@code BaseMapper<Emp>} 就获得了整套通用 CRUD，
 * 一条 SQL 都不用写：insert / deleteById / deleteByMap / delete(Wrapper) /
 * deleteBatchIds / updateById / update(Wrapper) / selectById / selectBatchIds /
 * selectByMap / selectOne / selectCount / selectList / selectMaps / selectObjs /
 * selectPage / selectMapsPage / selectCount(Wrapper) 等，
 * 实测初始化后共注入 <b>20 条</b> MappedStatement（含 BaseMapper 自带的分页与批量变体）。</p>
 *
 * <p>需要复杂 SQL（多表 JOIN、聚合）时仍然写 {@code @Select}：
 * 条件构造器面向单表，硬扛复杂查询只会更难维护。</p>
 */
public interface EmpPlusMapper extends BaseMapper<Emp> {

    /** 部门维度统计：MP 的条件构造器不擅长 GROUP BY，这类查询继续手写 */
    @Select("SELECT d.dept_id AS deptId, d.dept_name AS deptName, "
            + "COUNT(e.emp_id) AS empCount, IFNULL(SUM(e.salary), 0) AS totalSalary "
            + "FROM sys_dept d LEFT JOIN emp e "
            + "ON e.dept_id = d.dept_id AND e.status = 1 AND e.del_flag = 0 "
            + "GROUP BY d.dept_id, d.dept_name ORDER BY d.dept_id")
    List<Map<String, Object>> selectDeptStat();

    /** 按部门统计在职人数，演示手写 SQL 与 Wrapper 混用 */
    @Select("SELECT COUNT(*) FROM emp WHERE dept_id = #{deptId} AND status = 1 AND del_flag = 0")
    long countOnJobByDept(@Param("deptId") Integer deptId);

    /** 薪资高于给定值的员工数（用于验证 @TableLogic 自动追加的 del_flag 条件） */
    @Select("SELECT COUNT(*) FROM emp WHERE salary >= #{min} AND del_flag = 0")
    long countByMinSalary(@Param("min") BigDecimal min);
}
