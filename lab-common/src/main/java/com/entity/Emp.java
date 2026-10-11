package com.lab.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 员工实体，对应 emp 表（17 列）。
 *
 * <p>设计约定（与报告第五节问题 3、附录第 14 条对应）：</p>
 * <ul>
 *   <li><b>必须有公开无参构造</b>：MyBatis 通过 ObjectFactory 反射创建实体，
 *       若被 Lombok {@code @Builder} 挤掉了隐式无参构造，查询会抛
 *       {@code ReflectionException: Error instantiating class}。因此这里显式写
 *       {@code @NoArgsConstructor}，并且全项目约定实体不使用 {@code @Builder}。</li>
 *   <li><b>业务状态与逻辑删除分离</b>：{@code status} 表示 1 在职 / 0 离职，
 *       {@code delFlag} 才是 {@code @TableLogic} 的载体。二者若合并到一个字段，
 *       MyBatis-Plus 会把"离职"当成"已删除"，查询与删除语义整体反转。</li>
 *   <li><b>乐观锁</b>：{@code version} 上标 {@code @Version}，由 MP 在 updateById 时校验。</li>
 * </ul>
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@TableName("emp")
public class Emp implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增；IdType.AUTO 保证 MP 使用自增策略而非雪花算法 */
    @TableId(value = "emp_id", type = IdType.AUTO)
    private Integer empId;

    /** 工号（业务唯一键） */
    @TableField("emp_no")
    private String empNo;

    /** 姓名 */
    @TableField("emp_name")
    private String empName;

    /** 性别：男 / 女 */
    @TableField("gender")
    private String gender;

    /** 出生日期，允许为空 */
    @TableField("birth_date")
    private LocalDate birthDate;

    /** 邮箱（唯一键，允许为空） */
    @TableField("email")
    private String email;

    /** 手机号：敏感字段，日志输出前必须脱敏 */
    @TableField("phone")
    private String phone;

    /** 所属部门主键 */
    @TableField("dept_id")
    private Integer deptId;

    /** 部门名称（冗余列，用于演示单表条件下推） */
    @TableField("dept")
    private String dept;

    /** 岗位 */
    @TableField("post")
    private String post;

    /** 月薪 */
    @TableField("salary")
    private BigDecimal salary;

    /** 入职日期 */
    @TableField("hire_date")
    private LocalDate hireDate;

    /** 业务状态：1 在职 / 0 离职 */
    @TableField("status")
    private Integer status;

    /** 逻辑删除标记：0 正常 / 1 已删除 —— 只有这个字段可以承载 @TableLogic */
    @TableLogic(value = "0", delval = "1")
    @TableField("del_flag")
    private Integer delFlag;

    /** 乐观锁版本号 */
    @Version
    @TableField("version")
    private Integer version;

    /** 创建时间：由数据库默认值维护，插入时不显式赋值 */
    @TableField("create_time")
    private java.time.LocalDateTime createTime;

    /** 更新时间：由数据库 ON UPDATE 维护 */
    @TableField("update_time")
    private java.time.LocalDateTime updateTime;

    /**
     * 便捷构造：只填必填字段，用于测试里快速造数据。
     *
     * <p>注意这里刻意使用静态工厂而不是 Lombok {@code @Builder}，
     * 既保留无参构造，又避免"链式构造"把必需字段漏成 null。</p>
     */
    public static Emp of(String empNo, String empName, Integer deptId, String dept, BigDecimal salary) {
        Emp emp = new Emp();
        emp.setEmpNo(empNo);
        emp.setEmpName(empName);
        emp.setGender("男");
        emp.setDeptId(deptId);
        emp.setDept(dept);
        emp.setPost("工程师");
        emp.setSalary(salary);
        emp.setHireDate(LocalDate.of(2025, 1, 1));
        emp.setStatus(1);
        emp.setDelFlag(0);
        emp.setVersion(0);
        return emp;
    }
}
