-- =====================================================================
-- 01_schema.sql · 建库建表
--   库：ssm_emp（utf8mb4 / utf8mb4_0900_ai_ci）
--   表：emp（17 列）、sys_dept（8 列）
-- 设计要点：
--   1) 业务状态 status 与逻辑删除 del_flag 拆成两个字段，语义互不干扰；
--   2) del_flag 供 MyBatis-Plus 的 @TableLogic 专用；
--   3) emp_no / email 建唯一键，用于验证"逻辑删除与唯一键冲突"；
--   4) 为多条件查询与统计建立组合索引。
-- 执行：mysql -u root -p < sql/01_schema.sql
-- =====================================================================

DROP DATABASE IF EXISTS ssm_emp;
CREATE DATABASE ssm_emp
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE ssm_emp;

-- ---------------------------------------------------------------------
-- 部门维表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS sys_dept;
CREATE TABLE sys_dept
(
    dept_id     INT AUTO_INCREMENT COMMENT '部门主键',
    dept_code   VARCHAR(20)  NOT NULL COMMENT '部门编码（业务唯一键）',
    dept_name   VARCHAR(50)  NOT NULL COMMENT '部门名称',
    location    VARCHAR(50)  DEFAULT NULL COMMENT '办公地点',
    manager     VARCHAR(30)  DEFAULT NULL COMMENT '部门负责人',
    budget      DECIMAL(12, 2) DEFAULT NULL COMMENT '年度预算（元）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (dept_id),
    UNIQUE KEY uk_dept_code (dept_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='部门维表';

-- ---------------------------------------------------------------------
-- 员工表（在原 emp 表 8 个基础字段上扩充至 17 列）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS emp;
CREATE TABLE emp
(
    emp_id      INT AUTO_INCREMENT COMMENT '员工主键',
    emp_no      VARCHAR(20)    NOT NULL COMMENT '工号（业务唯一键）',
    emp_name    VARCHAR(30)    NOT NULL COMMENT '姓名',
    gender      CHAR(1)        NOT NULL DEFAULT '男' COMMENT '性别：男 / 女',
    birth_date  DATE           DEFAULT NULL COMMENT '出生日期',
    email       VARCHAR(60)    DEFAULT NULL COMMENT '邮箱（唯一键，允许空）',
    phone       VARCHAR(20)    DEFAULT NULL COMMENT '手机号（敏感字段，日志需脱敏）',
    dept_id     INT            DEFAULT NULL COMMENT '所属部门（外键 -> sys_dept.dept_id）',
    dept        VARCHAR(50)    DEFAULT NULL COMMENT '部门名称（有意保留的冗余，便于单表查询演示）',
    post        VARCHAR(50)    DEFAULT NULL COMMENT '岗位',
    salary      DECIMAL(10, 2) DEFAULT NULL COMMENT '月薪（元）',
    hire_date   DATE           DEFAULT NULL COMMENT '入职日期',
    status      TINYINT        NOT NULL DEFAULT 1 COMMENT '业务状态：1 在职 / 0 离职',
    del_flag    TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除（MyBatis-Plus @TableLogic 专用）',
    version     INT            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (emp_id),
    UNIQUE KEY uk_emp_no (emp_no),
    UNIQUE KEY uk_emp_email (email),
    KEY idx_emp_dept (dept_id),
    KEY idx_emp_status_del (status, del_flag),
    KEY idx_emp_salary (salary),
    KEY idx_emp_hire (hire_date),
    CONSTRAINT fk_emp_dept FOREIGN KEY (dept_id) REFERENCES sys_dept (dept_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='员工表';
