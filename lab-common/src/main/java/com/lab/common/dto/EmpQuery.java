package com.lab.common.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 多条件查询载体：把散落在各处的查询条件收敛成一个对象。
 *
 * <p>关键设计：{@link #safeOrderBy} 与 {@link #asc} 服务于"动态列名"。
 * 排序列名无法用 {@code #{}} 绑定（预编译占位符不能做标识符），只能用 {@code ${}}，
 * 因此这里做了<b>白名单收敛</b>：非法值一律回落到 {@code emp_id}，
 * 从源头上消除"order by 注入"。</p>
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class EmpQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 排序字段白名单：对外只暴露逻辑名，避免直接把数据库列名交给调用方 */
    public static final List<String> ORDER_BY_WHITELIST = Collections.unmodifiableList(
            Arrays.asList("empId", "empName", "salary", "hireDate", "deptId"));

    /** 逻辑名 -> 物理列名 */
    private static final java.util.Map<String, String> ORDER_BY_COLUMN_MAP;

    static {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        map.put("empId", "emp_id");
        map.put("empName", "emp_name");
        map.put("salary", "salary");
        map.put("hireDate", "hire_date");
        map.put("deptId", "dept_id");
        ORDER_BY_COLUMN_MAP = Collections.unmodifiableMap(map);
    }

    /** 姓名模糊匹配 */
    private String empName;

    /** 部门主键，等值匹配 */
    private Integer deptId;

    /** 岗位模糊匹配 */
    private String post;

    /** 薪资下限（含） */
    private BigDecimal minSalary;

    /** 薪资上限（含） */
    private BigDecimal maxSalary;

    /** 入职日期下限（含） */
    private LocalDate hireDateFrom;

    /** 入职日期上限（含） */
    private LocalDate hireDateTo;

    /** 业务状态：1 在职 / 0 离职；null 表示不限 */
    private Integer status;

    /** 是否只查未逻辑删除的数据，默认 true */
    private Boolean onlyAlive = Boolean.TRUE;

    /** 排序逻辑名，默认按主键 */
    private String orderBy = "empId";

    /** 是否升序，默认升序 */
    private Boolean asc = Boolean.TRUE;

    /**
     * 解析出可安全拼接的物理列名。
     *
     * @return 白名单命中的列名；任何非法输入（null、空串、注入串）都回落为 emp_id
     */
    public String getSafeOrderBy() {
        if (orderBy == null) {
            return "emp_id";
        }
        String column = ORDER_BY_COLUMN_MAP.get(orderBy.trim());
        return column == null ? "emp_id" : column;
    }

    /** 排序方向是否升序，null 视为升序 */
    public boolean isAscending() {
        return asc == null || asc;
    }
}
