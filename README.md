# mybit.lab

《Java 框架技术（一）》实验一：**纯 Java + MyBatis / MyBatis-Plus 环境搭建与 CRUD 实操**。

多模块 Maven 聚合工程，不使用 Spring、不使用任何 starter：SqlSessionFactory、数据源、
拦截器与 Mapper 注册全部在代码里手工装配。

- 作者：赵亮　学号：20241850　班级：4 班
- 环境：macOS 26.3.1 / OpenJDK 17.0.19（编译级别 1.8）/ Maven 3.10.0 / MySQL 9.6.0
- 规模：15 个测试类 / **116 个测试用例**，全部通过

---

## 一、在 IDEA 里跑起来

### 准备数据库（两个表）

```bash
# 启动工程自带的 MySQL 实例（127.0.0.1:3307）
bash scripts/start-mysql.sh

# 建库建表 + 导入数据
mysql -u root -p < sql/01_schema.sql     # 建库 ssm_emp + 两张表
mysql -u root -p < sql/02_seed.sql       # 6 个部门 + 16 名员工
mysql -u root -p < sql/03_verify.sql     # 核对：16 / 15 / 1 / 12 / 3
```

建出来的是两张表：

| 表 | 列数 | 说明 |
| --- | --- | --- |
| `emp` | 17 | 员工表：工号、姓名、性别、出生日期、邮箱、电话、部门、岗位、薪资、入职日期、状态、逻辑删除标记、版本号等 |
| `sys_dept` | 8 | 部门维表：编码、名称、地点、负责人、预算等 |

### 在 IDEA 里打开

1. `File → Open`，选中**根目录的 `pom.xml`**，选 **Open as Project**（IDEA 会自动识别 4 个模块）；
2. 等 Maven 导入完成；
3. 打开 `lab-bootstrap/src/main/java/com/lab/LabApplication.java`，**右键 → Run**；
4. 控制台会依次打印两个表的结构与数据、条件查询、分页、部门统计、N+1 对照、
   MyBatis-Plus 逻辑删除、以及"插入→更新→删除→回滚"的写操作演示。

连接参数在 `lab-mybatis/src/main/resources/db.properties`（已被 `.gitignore` 排除）。

### 跑测试

```bash
mvn test                                   # 全量：116 个用例
mvn test -Dtest=EmpMapperTest              # 单个测试类
```

---

## 二、模块划分

| 模块 | 职责 | 依赖 |
| --- | --- | --- |
| `lab-common` | 实体 / DTO / 工具 / 异常 / 连接参数解析 | 只依赖 mybatis-plus-annotation |
| `lab-mybatis` | 原生 MyBatis 轨：XML + 注解 Mapper、会话模板、自研分页、慢 SQL 插件 | **不含 MyBatis-Plus** |
| `lab-mybatis-plus` | MyBatis-Plus 轨：BaseMapper、条件构造器、分页插件、逻辑删除 | 不含原生轨 |
| `lab-bootstrap` | 汇合点：可运行入口 + 15 个测试类 | 同时依赖两条轨 |

**依赖隔离原则**：`lab-mybatis` 的依赖里不出现 MyBatis-Plus，`lab-mybatis-plus` 里也不出现
原生轨的会话模板。两条轨只在 `lab-bootstrap` 汇聚，通过一致性测试相互校验。
版本统一由父工程 `dependencyManagement` 仲裁，子模块不重复声明 `version`。

```
mybit.lab/
├── pom.xml                        聚合父工程（packaging=pom，版本中枢）
├── sql/                           01 建表 / 02 种子数据 / 03 基线核对
├── scripts/                       MySQL 启停、一键测试、生成报告
├── demo.sh                        答辩演示脚本
├── lab-common/                    实体 / DTO / 工具 / 异常
├── lab-mybatis/                   原生 MyBatis 轨
├── lab-mybatis-plus/              MyBatis-Plus 轨
└── lab-bootstrap/                 可运行入口 + 15 个测试类
```

---

## 三、验收要点

| 项 | 结果 |
| --- | --- |
| 构建 | `mvn install` 四个模块全部通过 |
| 测试 | 15 个测试类 / 116 个用例，Failures 0 / Errors 0 |
| 数据基线 | 物理 16 行 / 未删除 15 行 / 已逻辑删除 1 行 / 在职 12 行 / 离职 3 行 |
| N+1 对照 | 嵌套写法 7 条 SQL vs JOIN 写法 1 条，数据逐字段一致 |
| 索引 | `EXPLAIN` 实测命中 `idx_emp_status_del` 组合索引 |
| 禁用 Spring | `NoSpringDependencyTest` 运行期校验通过 |
| 模块隔离 | `lab-mybatis` 的依赖树里没有 MyBatis-Plus |

---

## 四、安全约定

- 连接凭据一律走 `-D` 系统属性或环境变量，配置文件只允许出现 `${...}` 占位符；
- `db.properties`、`jdbc.properties`、`.env`、`target/`、`_runtime/` 均已被 `.gitignore` 排除；
- `SecurityScanTest` 把这些要求写成了检查。
