package com.lab.common.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 部门统计视图对象：承载"部门 + 在职人数 + 薪资聚合"的一行结果。
 *
 * <p>它是 N+1 对照实验的返回类型：无论用"1 条 LEFT JOIN 聚合"还是
 * "1 条主查询 + N 条子查询"实现，最终都必须组装成这个对象，
 * 于是"两种写法结果是否一致"就变成了可以逐字段断言的命题。</p>
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class DeptStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 部门主键 */
    private Integer deptId;

    /** 部门名称 */
    private String deptName;

    /** 在职员工数 */
    private Integer empCount;

    /** 薪资合计 */
    private BigDecimal totalSalary;

    /** 薪资最高值 */
    private BigDecimal maxSalary;

    /** 薪资最低值 */
    private BigDecimal minSalary;

    /**
     * 平均薪资，由代码计算而非依赖 SQL 的 AVG 返回值。
     *
     * <p>这样做是为了绕开"不同实现下 AVG 的小数位与舍入方式可能不同"的干扰，
     * 让三轨/多写法的比较只针对确定性的整数与合计值。</p>
     */
    public BigDecimal getAvgSalary() {
        if (empCount == null || empCount == 0 || totalSalary == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return totalSalary.divide(BigDecimal.valueOf(empCount), 2, RoundingMode.HALF_UP);
    }
}
