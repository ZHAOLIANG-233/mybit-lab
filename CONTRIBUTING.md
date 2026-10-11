# 协作说明

这是个课程实验工程，如果你把它拉下来想跑一遍，按下面几步就行。

## 环境要求

- JDK 17
- Maven 3.8+
- MySQL 8.0 / 9.x

## 跑起来

```bash
# 1) 起一个实验用的 MySQL 实例（独立数据目录，不碰你本机已有的库）
bash scripts/start-lab-mysql.sh

# 2) 建库建表、导入数据
mysql -u root -p < sql/01_schema.sql
mysql -u root -p < sql/02_seed.sql
mysql -u root -p < sql/03_verify.sql

# 3) 配置连接参数（复制模板后填自己的口令）
cp .lab.env.example .lab.env

# 4) 跑测试 / 跑演示
mvn test
mvn -pl lab-bootstrap exec:java
```

## 注意

- **不要提交 `db.properties`、`jdbc.properties`、`.lab.env`**：这些是本机凭据，已在 `.gitignore` 里；
- 提交前请先跑 `mvn test`，116 个用例应全部通过；
- 提交信息用 `feat/fix/test/docs/chore` 前缀 + 中文说明，跟现有历史保持一致。
