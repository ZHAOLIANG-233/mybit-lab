package com.lab.mybatis.service.impl;

import com.lab.common.dto.DeptStatVO;
import com.lab.common.dto.EmpQuery;
import com.lab.common.dto.PageQuery;
import com.lab.common.dto.PageResult;
import com.lab.common.entity.Emp;
import com.lab.common.exception.BusinessException;
import com.lab.common.exception.ErrorCode;
import com.lab.common.util.Validator;
import com.lab.mybatis.config.MyBatisSessionTemplate;
import com.lab.mybatis.mapper.EmpMapper;
import com.lab.mybatis.service.EmpService;

import java.math.BigDecimal;
import java.util.List;

/**
 * 员工业务实现（原生 MyBatis 轨）。
 *
 * <p>本类里没有一行 {@code openSession / commit / rollback / close}：
 * 事务边界全部交给 {@link MyBatisSessionTemplate}。
 * 这正是"会话模板"的价值——业务方法只表达"做什么"，
 * 漏写 commit 导致的"静默回滚"从根上被消除。</p>
 */
public class EmpServiceImpl implements EmpService {

    /** 会话模板：默认从 MyBatisSessionFactory 取会话 */
    private final MyBatisSessionTemplate template;

    public EmpServiceImpl() {
        this(new MyBatisSessionTemplate());
    }

    public EmpServiceImpl(MyBatisSessionTemplate template) {
        this.template = template;
    }

    @Override
    public Integer save(final Emp emp) {
        // 校验放在事务外：参数不合法时不必开数据库连接
        Validator.validateForInsert(emp);
        return template.withSession(session -> {
            EmpMapper mapper = session.getMapper(EmpMapper.class);
            mapper.insert(emp);
            // insert 依赖 useGeneratedKeys 回填；若这里拿到 null，说明映射缺了那对属性
            if (emp.getEmpId() == null) {
                throw new BusinessException(ErrorCode.DATA_ACCESS_ERROR,
                        "插入后主键未回填，请检查 <insert> 是否声明 useGeneratedKeys/keyProperty");
            }
            return emp.getEmpId();
        });
    }

    @Override
    public Emp getById(final Integer empId) {
        Validator.requireId(empId, "empId");
        return template.withReadOnlySession(session ->
                session.getMapper(EmpMapper.class).selectById(empId));
    }

    @Override
    public List<Emp> listAll() {
        return template.withReadOnlySession(session ->
                session.getMapper(EmpMapper.class).selectAll());
    }

    @Override
    public int updateSelective(final Emp emp) {
        if (emp == null || emp.getEmpId() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "更新时必须提供 empId");
        }
        if (emp.getStatus() != null && emp.getStatus() != 0 && emp.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "status 只能是 0/1");
        }
        return template.withSession(session ->
                session.getMapper(EmpMapper.class).updateDynamic(emp));
    }

    @Override
    public int removeById(final Integer empId) {
        Validator.requireId(empId, "empId");
        return template.withSession(session ->
                session.getMapper(EmpMapper.class).deleteById(empId));
    }

    @Override
    public List<Emp> searchByName(final String keyword) {
        EmpQuery query = new EmpQuery();
        query.setEmpName(keyword);
        query.setOrderBy("empId");
        return search(query);
    }

    @Override
    public List<Emp> search(final EmpQuery query) {
        final EmpQuery effective = query == null ? new EmpQuery() : query;
        return template.withReadOnlySession(session ->
                session.getMapper(EmpMapper.class).selectByCondition(effective));
    }

    @Override
    public PageResult<Emp> page(final EmpQuery query, final PageQuery page) {
        final EmpQuery effectiveQuery = query == null ? new EmpQuery() : query;
        final PageQuery effectivePage = page == null ? new PageQuery() : page;
        return template.withReadOnlySession(session -> {
            EmpMapper mapper = session.getMapper(EmpMapper.class);
            // 先用同一套条件取总数，再取当前页：两条语句共用 conditionWhere，口径天然一致
            long total = mapper.countByCondition(effectiveQuery);
            List<Emp> records = total == 0
                    ? java.util.Collections.emptyList()
                    : mapper.selectPage(effectiveQuery, effectivePage);
            return new PageResult<>(total, effectivePage.getSafePageNum(),
                    effectivePage.getSafePageSize(), records);
        });
    }

    @Override
    public int saveBatch(final List<Emp> emps) {
        if (emps == null || emps.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "批量新增的列表不能为空");
        }
        for (Emp emp : emps) {
            Validator.validateForInsert(emp);
        }
        // 整批放在同一个事务里：要么全成功，要么全回滚，不留下"插了一半"的数据
        return template.withSession(session ->
                session.getMapper(EmpMapper.class).insertBatch(emps));
    }

    @Override
    public List<DeptStatVO> deptStatistics() {
        return template.withReadOnlySession(session ->
                session.getMapper(EmpMapper.class).selectDeptStatByJoin());
    }

    @Override
    public int raiseSalary(final Integer firstId, final Integer secondId, final BigDecimal delta) {
        Validator.requireId(firstId, "firstId");
        Validator.requireId(secondId, "secondId");
        if (delta == null || delta.signum() == 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "调薪幅度不能为 0");
        }
        return template.withSession(session -> {
            EmpMapper mapper = session.getMapper(EmpMapper.class);
            int affected = 0;

            Emp first = mapper.selectById(firstId);
            if (first == null) {
                throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "员工不存在：" + firstId);
            }
            Emp patch = new Emp();
            patch.setEmpId(firstId);
            patch.setSalary(first.getSalary().add(delta));
            affected += mapper.updateDynamic(patch);

            Emp second = mapper.selectById(secondId);
            if (second == null) {
                // 抛异常 → 模板 rollback → 上面那次 update 一并撤销
                throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "员工不存在：" + secondId);
            }
            Emp patch2 = new Emp();
            patch2.setEmpId(secondId);
            patch2.setSalary(second.getSalary().add(delta));
            affected += mapper.updateDynamic(patch2);

            return affected;
        });
    }
}
