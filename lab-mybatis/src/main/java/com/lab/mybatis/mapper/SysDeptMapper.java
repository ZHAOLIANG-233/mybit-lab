package com.lab.mybatis.mapper;

import com.lab.common.entity.SysDept;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 部门 Mapper（XML 轨）。
 *
 * <p>与 {@link EmpMapper} 分开定义，是为了让"部门聚合查询"与"员工 CRUD"各自演进，
 * 避免一个 Mapper 无限膨胀。</p>
 */
public interface SysDeptMapper {

    /** 按主键查询部门 */
    SysDept selectById(@Param("deptId") Integer deptId);

    /** 查询全部部门 */
    List<SysDept> selectAll();

    /** 按部门编码查询（唯一键） */
    SysDept selectByCode(@Param("deptCode") String deptCode);

    /** 统计部门总数 */
    long countAll();
}
