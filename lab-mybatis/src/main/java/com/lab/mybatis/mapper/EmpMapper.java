package com.lab.mybatis.mapper;

import com.lab.common.dto.DeptStatVO;
import com.lab.common.dto.EmpQuery;
import com.lab.common.dto.PageQuery;
import com.lab.common.entity.Emp;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 员工 Mapper（XML 轨）：接口只声明方法，SQL 全部写在映射文件里。
 *
 * <p>映射文件必须与接口<b>同包同路径</b>存放
 * （{@code src/main/resources/com/lab/mybatis/mapper/EmpMapper.xml}）：
 * {@code <package>} 扫描时 MyBatis 只按"接口全限定名把 . 换成 / 再加 .xml"查找，
 * 放到 {@code resources/mapper/} 下会注册成功但语句找不到，
 * 运行期报 {@code BindingException: Invalid bound statement}。</p>
 *
 * <p>所有多参数方法都显式标 {@code @Param}，否则 MyBatis 只能给出
 * {@code arg0/param1} 这类名字，{@code #{q}} 会解析失败。</p>
 */
public interface EmpMapper {

    // ================= 基础 CRUD =================

    /** 按主键查询（含已逻辑删除的行——原生轨不自动过滤） */
    Emp selectById(@Param("empId") Integer empId);

    /** 查询全部（含已逻辑删除的行，共 16 条） */
    List<Emp> selectAll();

    /** 新增，并回填自增主键（依赖 useGeneratedKeys / keyProperty） */
    int insert(Emp emp);

    /** 按主键物理删除 */
    int deleteById(@Param("empId") Integer empId);

    /** 全字段更新 */
    int update(Emp emp);

    /**
     * 动态更新：只更新非空字段，由 {@code <set>} 自动去掉多余逗号。
     *
     * @return 受影响行数；id 不存在时为 0（不抛异常，便于调用方自行判断）
     */
    int updateDynamic(@Param("emp") Emp emp);

    // ================= 动态 SQL / 多条件查询 =================

    /** 多条件查询，条件片段抽取为 {@code <sql>} 并被两种容器复用 */
    List<Emp> selectByCondition(@Param("q") EmpQuery query);

    /** 与上面共用同一条件片段的计数查询，保证"列表"与"总数"口径一致 */
    long countByCondition(@Param("q") EmpQuery query);

    /** 分页查询：在条件之上追加 LIMIT，由 PageQuery 归一化 offset/size */
    List<Emp> selectPage(@Param("q") EmpQuery query, @Param("page") PageQuery page);

    /** 演示 {@code <trim>} 与 {@code <where>} 的等价性 */
    List<Emp> selectByConditionWithTrim(@Param("q") EmpQuery query);

    /** 三级分支：优先按姓名精确查 → 否则按部门查 → 否则兜底查在职 */
    List<Emp> selectByPriority(@Param("empName") String empName, @Param("deptId") Integer deptId);

    /** 批量插入（多行 VALUES），要求 useGeneratedKeys 能逐行回填主键 */
    int insertBatch(@Param("list") List<Emp> list);

    /** 批量删除，用 {@code <foreach>} 展开 IN 列表 */
    int deleteBatch(@Param("ids") List<Integer> ids);

    /** 薪资区间查询，演示边界相等的情况（BETWEEN 为闭区间） */
    List<Emp> selectBySalaryRange(@Param("min") BigDecimal min, @Param("max") BigDecimal max);

    // ================= 统计 / 关联 =================

    /** 部门统计（JOIN + GROUP BY，一次查询完成） */
    List<DeptStatVO> selectDeptStatByJoin();

    /** 部门统计（N+1 写法：先查部门，再逐个部门查聚合结果） */
    DeptStatVO selectDeptStatByDeptId(@Param("deptId") Integer deptId);

    /** 按部门查在职员工（供 N+1 的 &lt;collection select&gt; 调用） */
    List<Emp> selectEmpsByDeptId(@Param("deptId") Integer deptId);

    /** 查询所有部门（含 emps 集合，N+1 写法） */
    List<com.lab.common.entity.SysDept> selectDeptWithEmpsNPlusOne();

    /** 查询所有部门（JOIN 聚合一写法，columnPrefix 组装 emps） */
    List<com.lab.common.entity.SysDept> selectDeptWithEmpsJoin();

    /** 按岗位统计人数，返回 Map 列表（演示 resultType=map） */
    List<Map<String, Object>> countGroupByPost();

    /** 查最小主键，用于测试中定位刚插入的行 */
    Integer selectMaxId();
}
