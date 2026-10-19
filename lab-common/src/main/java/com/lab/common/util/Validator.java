package com.lab.common.util;

import com.lab.common.entity.Emp;
import com.lab.common.exception.ValidationException;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * 实体级参数校验器。
 *
 * <p>纯 MyBatis 轨没有 Spring 的 {@code @Valid}，校验只能手写。
 * 把它抽成独立的工具类，而不是散落在 Service 的 if 里，
 * 好处是"同一份规则可以被测试单独驱动"，也能被两条轨共用。</p>
 */
public final class Validator {

    /** 工号：字母 + 数字，长度 4 ~ 20 */
    private static final Pattern EMP_NO = Pattern.compile("^[A-Za-z0-9]{4,20}$");

    /** 邮箱：够用即可，不追求 RFC 完备 */
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /** 手机号：11 位数字，以 1 开头 */
    private static final Pattern PHONE = Pattern.compile("^1\\d{10}$");

    /** 薪资上限：超过此值基本可判定为录入错误 */
    private static final BigDecimal SALARY_UPPER = new BigDecimal("1000000");

    private Validator() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 校验新增员工的必填项与格式。
     *
     * @param emp 待校验实体
     * @throws ValidationException 任一规则不满足时抛出，消息中带出具体字段
     */
    public static void validateForInsert(Emp emp) {
        if (emp == null) {
            throw new ValidationException("员工对象不能为 null");
        }
        requireText(emp.getEmpNo(), "empNo（工号）");
        if (!EMP_NO.matcher(emp.getEmpNo()).matches()) {
            throw new ValidationException("empNo 格式非法，应为 4~20 位字母或数字：" + emp.getEmpNo());
        }
        requireText(emp.getEmpName(), "empName（姓名）");
        if (emp.getEmpName().length() > 30) {
            throw new ValidationException("empName 超长（>30）：" + emp.getEmpName());
        }
        if (emp.getEmail() != null && !emp.getEmail().isEmpty()
                && !EMAIL.matcher(emp.getEmail()).matches()) {
            throw new ValidationException("email 格式非法：" + emp.getEmail());
        }
        if (emp.getPhone() != null && !emp.getPhone().isEmpty()
                && !PHONE.matcher(emp.getPhone()).matches()) {
            throw new ValidationException("phone 格式非法，应为 11 位数字：" + emp.getPhone());
        }
        if (emp.getSalary() != null) {
            if (emp.getSalary().signum() < 0) {
                throw new ValidationException("salary 不能为负数：" + emp.getSalary());
            }
            if (emp.getSalary().compareTo(SALARY_UPPER) > 0) {
                throw new ValidationException("salary 超出合理上限：" + emp.getSalary());
            }
        }
        // 业务状态只允许 0 / 1，避免把 2 之类的脏值写进库
        if (emp.getStatus() != null && emp.getStatus() != 0 && emp.getStatus() != 1) {
            throw new ValidationException("status 只能是 0（离职）或 1（在职）：" + emp.getStatus());
        }
    }

    /** 校验主键可用，用于按 id 查询/删除的场景 */
    public static void requireId(Integer id, String field) {
        if (id == null || id <= 0) {
            throw new ValidationException(field + " 必须是正整数：" + id);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException(field + " 不能为空");
        }
    }
}
