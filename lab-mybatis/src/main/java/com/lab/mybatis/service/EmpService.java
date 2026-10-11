package com.lab.mybatis.service;

import com.lab.common.dto.DeptStatVO;
import com.lab.common.dto.EmpQuery;
import com.lab.common.dto.PageQuery;
import com.lab.common.dto.PageResult;
import com.lab.common.entity.Emp;

import java.math.BigDecimal;
import java.util.List;

/**
 * 员工业务接口。
 *
 * <p>方法设计遵循两个原则：</p>
 * <ol>
 *   <li><b>写操作返回受影响行数</b>，让调用方自己决定"0 行算不算失败"，
 *       而不是在 Service 里替业务下结论；</li>
 *   <li><b>分页与多条件查询返回领域对象</b>（{@link PageResult}/{@link Emp}），
 *       不把 MyBatis 的 Map 或 ResultSet 泄露到业务层。</li>
 * </ol>
 */
public interface EmpService {

    /** 新增员工：先校验，再在托管事务中插入，并返回回填后的主键 */
    Integer save(Emp emp);

    /** 按主键查询；不存在返回 null */
    Emp getById(Integer empId);

    /** 查询全部（含逻辑删除行） */
    List<Emp> listAll();

    /** 动态更新：只更新非空字段 */
    int updateSelective(Emp emp);

    /** 按主键物理删除 */
    int removeById(Integer empId);

    /** 按姓名模糊查询在职员工 */
    List<Emp> searchByName(String keyword);

    /** 多条件查询 */
    List<Emp> search(EmpQuery query);

    /** 分页查询（自研 COUNT + LIMIT） */
    PageResult<Emp> page(EmpQuery query, PageQuery page);

    /** 批量新增（单条多行 VALUES） */
    int saveBatch(List<Emp> emps);

    /** 部门统计（JOIN 聚合） */
    List<DeptStatVO> deptStatistics();

    /**
     * 调薪：演示"业务校验失败必须整体回滚"。
     *
     * <p>该方法内会更新两条记录，若第二条的员工不存在则抛异常，
     * 第一条的修改必须一并回滚，否则会出现"一半成功"的脏数据。</p>
     *
     * @param firstId  第一个员工 id
     * @param secondId 第二个员工 id（刻意传不存在的 id 即可触发回滚）
     * @param delta    调薪幅度（元），可为负
     * @return 两条记录的实际受影响行数之和
     */
    int raiseSalary(Integer firstId, Integer secondId, BigDecimal delta);
}
