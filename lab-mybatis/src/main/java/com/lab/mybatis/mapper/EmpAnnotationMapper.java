package com.lab.mybatis.mapper;

import com.lab.common.entity.Emp;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 员工 Mapper（注解轨）：不写 XML，SQL 直接标在方法上。
 *
 * <p>它是"三轨一致性测试"的第三条腿：同一份查询用 XML、注解、MyBatis-Plus
 * 三种方式各实现一遍，再断言逐字段相等。差异会被测试固定下来，
 * 而不是靠人工比对。</p>
 *
 * <p>与 XML 轨的两点刻意差异：</p>
 * <ul>
 *   <li>列清单以 Java 常量拼接，避免在注解里写超长字符串导致换行混乱；</li>
 *   <li>同样使用 {@code @Results} 显式声明映射，即使全局驼峰开关被关闭也不会静默为 null。</li>
 * </ul>
 */
public interface EmpAnnotationMapper {

    /** 与 XML 轨共用的列清单（不含 create_time/update_time，注解轨只关心业务字段） */
    String COLUMNS = "emp_id, emp_no, emp_name, gender, birth_date, email, phone, "
            + "dept_id, dept, post, salary, hire_date, status, del_flag, version";

    /**
     * 按主键查询。
     *
     * <p>{@code @Results} 指定了 id，后续方法可用 {@code @ResultMap("empAnnotationMap")} 复用，
     * 不必重复声明映射。</p>
     */
    @Results(id = "empAnnotationMap", value = {
            @Result(property = "empId", column = "emp_id", id = true),
            @Result(property = "empNo", column = "emp_no"),
            @Result(property = "empName", column = "emp_name"),
            @Result(property = "gender", column = "gender"),
            @Result(property = "birthDate", column = "birth_date"),
            @Result(property = "email", column = "email"),
            @Result(property = "phone", column = "phone"),
            @Result(property = "deptId", column = "dept_id"),
            @Result(property = "dept", column = "dept"),
            @Result(property = "post", column = "post"),
            @Result(property = "salary", column = "salary"),
            @Result(property = "hireDate", column = "hire_date"),
            @Result(property = "status", column = "status"),
            @Result(property = "delFlag", column = "del_flag"),
            @Result(property = "version", column = "version")
    })
    @Select("SELECT " + COLUMNS + " FROM emp WHERE emp_id = #{empId}")
    Emp selectById(@Param("empId") Integer empId);

    /**
     * 查询全部未逻辑删除的在职员工。
     *
     * <p>用 {@code @ResultMap} 复用上面的映射，避免第二次抄列名（抄错列名不会报错，
     * 只会静默为 null，是最难查的一类缺陷）。</p>
     */
    @org.apache.ibatis.annotations.ResultMap("empAnnotationMap")
    @Select("SELECT " + COLUMNS + " FROM emp WHERE status = 1 AND del_flag = 0 ORDER BY emp_id")
    List<Emp> selectAliveOnJob();

    /** 按薪资区间查询（闭区间） */
    @org.apache.ibatis.annotations.ResultMap("empAnnotationMap")
    @Select("SELECT " + COLUMNS + " FROM emp "
            + "WHERE del_flag = 0 AND salary BETWEEN #{min} AND #{max} ORDER BY salary")
    List<Emp> selectBySalaryRange(@Param("min") BigDecimal min, @Param("max") BigDecimal max);

    /**
     * 新增并回填主键。
     *
     * <p>注解写法下回填主键用 {@code @Options(useGeneratedKeys = true, keyProperty = "empId")}，
     * 与 XML 的两个属性等价。</p>
     */
    @Insert("INSERT INTO emp (emp_no, emp_name, gender, dept_id, dept, post, salary, hire_date, status, del_flag, version) "
            + "VALUES (#{empNo}, #{empName}, #{gender}, #{deptId}, #{dept}, #{post}, #{salary}, #{hireDate}, #{status}, 0, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "empId")
    int insert(Emp emp);

    /** 物理删除（清理测试数据用） */
    @Delete("DELETE FROM emp WHERE emp_id = #{empId}")
    int deleteById(@Param("empId") Integer empId);

    /** 与 XML 轨的计数对照 */
    @Select("SELECT COUNT(*) FROM emp WHERE del_flag = 0")
    long countAlive();
}
