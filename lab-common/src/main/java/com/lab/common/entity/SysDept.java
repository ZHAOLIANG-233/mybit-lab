package com.lab.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 部门实体，对应 sys_dept 表（8 列）。
 *
 * <p>它与 emp 构成一对多关系，是"部门统计"与"N+1 对照实验"的数据基础：
 * {@code emps} 字段是 N+1 场景下的 {@code <collection>} 载体，
 * 刻意标注 {@code @TableField(exist = false)} —— 它不是 sys_dept 的列，
 * 若忘记标注，MyBatis-Plus 会把 {@code emps} 当成真实字段拼进 SQL。</p>
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@TableName("sys_dept")
public class SysDept implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "dept_id", type = IdType.AUTO)
    private Integer deptId;

    /** 部门编码（业务唯一键） */
    @TableField("dept_code")
    private String deptCode;

    /** 部门名称 */
    @TableField("dept_name")
    private String deptName;

    /** 办公地点 */
    @TableField("location")
    private String location;

    /** 部门负责人，允许为空（财务部在种子数据中刻意留空） */
    @TableField("manager")
    private String manager;

    /** 年度预算 */
    @TableField("budget")
    private BigDecimal budget;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    /** 关联的在职员工列表：数据库无此列，仅供结果映射聚合使用 */
    @TableField(exist = false)
    private List<Emp> emps = new ArrayList<>();
}
