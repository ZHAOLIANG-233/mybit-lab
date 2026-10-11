-- =====================================================================
-- 03_verify.sql · 数据基线复核
--   期望结果：物理 16 行 / 未删除 15 行 / 已逻辑删除 1 行 / 在职 12 行 / 离职 3 行
-- 执行：mysql -u root -p < sql/03_verify.sql
-- =====================================================================

USE ssm_emp;

-- ① 基线总览：一行返回 5 个口径
SELECT COUNT(*)                                                   AS 物理行数,
       SUM(CASE WHEN del_flag = 0 THEN 1 ELSE 0 END)              AS 未删除行数,
       SUM(CASE WHEN del_flag = 1 THEN 1 ELSE 0 END)              AS 已逻辑删除行数,
       SUM(CASE WHEN status = 1 AND del_flag = 0 THEN 1 ELSE 0 END) AS 在职行数,
       SUM(CASE WHEN status = 0 AND del_flag = 0 THEN 1 ELSE 0 END) AS 离职行数
FROM emp;

-- ② 部门维度核对：6 个部门，含部门的在职员工数
SELECT d.dept_id,
       d.dept_name,
       COUNT(e.emp_id) AS 在职人数,
       IFNULL(SUM(e.salary), 0) AS 薪资合计
FROM sys_dept d
         LEFT JOIN emp e ON e.dept_id = d.dept_id AND e.status = 1 AND e.del_flag = 0
GROUP BY d.dept_id, d.dept_name
ORDER BY d.dept_id;

-- ③ 边界数据核对：空值字段与逻辑删除行
SELECT emp_id, emp_no, emp_name, email, phone, birth_date, status, del_flag
FROM emp
WHERE email IS NULL OR phone IS NULL OR birth_date IS NULL OR del_flag = 1
ORDER BY emp_id;

-- ④ 索引可用性核对：期望 key = idx_emp_status_del
EXPLAIN
SELECT emp_id, emp_name, salary
FROM emp
WHERE status = 1
  AND del_flag = 0
  AND salary >= 10000
ORDER BY salary DESC
LIMIT 0, 5;
