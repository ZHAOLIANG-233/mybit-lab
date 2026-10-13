#!/usr/bin/env bash
# =====================================================================
# demo.sh · 答辩一键演示
#
# 用法：在工程根目录执行
#   bash demo.sh
#
# 它会依次做：
#   1. 检查/启动 MySQL（没起就自动起）
#   2. 检查两张表和数据在不在（不在就自动建）
#   3. 跑 LabApplication，展示两个表的完整功能
#   4. 跑全量测试，展示 116 个用例通过
#   5. 打印 git 提交记录
# =====================================================================
set -uo pipefail
cd "$(dirname "$0")"

RED='\033[1;31m'; GREEN='\033[1;32m'; BLUE='\033[1;36m'; YELLOW='\033[1;33m'; NC='\033[0m'

say() { echo -e "${BLUE}$*${NC}"; }
ok()  { echo -e "${GREEN}  ✓ $*${NC}"; }
bad() { echo -e "${RED}  ✗ $*${NC}"; }
warn(){ echo -e "${YELLOW}  ! $*${NC}"; }
step(){ echo; echo -e "${BLUE}━━━ $* ━━━${NC}"; }

DB_PORT=3307
DB_PASS=123456
MYSQL="mysql --protocol=TCP -h 127.0.0.1 -P ${DB_PORT} -u root -p${DB_PASS}"

# ---------- 0. 环境检查 ----------
step "0. 检查环境"
command -v java >/dev/null || { bad "找不到 java，请先装 JDK 17"; exit 1; }
ok "Java: $(java -version 2>&1 | head -1)"
command -v mvn >/dev/null || { bad "找不到 mvn，请先装 Maven"; exit 1; }
ok "Maven: $(mvn -v 2>/dev/null | head -1)"

# ---------- 1. 数据库 ----------
step "1. 数据库"
if mysqladmin --protocol=TCP -h 127.0.0.1 -P ${DB_PORT} -u root -p${DB_PASS} ping >/dev/null 2>&1; then
    ok "MySQL 已在运行（127.0.0.1:${DB_PORT}）"
else
    warn "MySQL 没在跑，正在启动..."
    if [ -f scripts/start-mysql.sh ]; then
        bash scripts/start-mysql.sh >/dev/null 2>&1
    fi
    if mysqladmin --protocol=TCP -h 127.0.0.1 -P ${DB_PORT} -u root -p${DB_PASS} ping >/dev/null 2>&1; then
        ok "MySQL 启动成功"
    else
        bad "MySQL 启动失败。请在终端手动执行："
        echo "      brew services start mysql"
        echo "   然后把 sql/01_schema.sql 和 02_seed.sql 导入"
        exit 1
    fi
fi

# 检查两张表
TABLES=$(${MYSQL} -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ssm_emp' AND table_name IN ('emp','sys_dept');" 2>/dev/null)
if [ "${TABLES:-0}" = "2" ]; then
    EMP=$(${MYSQL} -N -e "SELECT COUNT(*) FROM ssm_emp.emp;" 2>/dev/null)
    DEPT=$(${MYSQL} -N -e "SELECT COUNT(*) FROM ssm_emp.sys_dept;" 2>/dev/null)
    ok "两张表都在：emp=${EMP} 行，sys_dept=${DEPT} 行"
else
    warn "表不存在，正在建库建表..."
    ${MYSQL} < sql/01_schema.sql 2>/dev/null
    ${MYSQL} < sql/02_seed.sql   2>/dev/null
    EMP=$(${MYSQL} -N -e "SELECT COUNT(*) FROM ssm_emp.emp;" 2>/dev/null)
    DEPT=$(${MYSQL} -N -e "SELECT COUNT(*) FROM ssm_emp.sys_dept;" 2>/dev/null)
    ok "建好了：emp=${EMP} 行，sys_dept=${DEPT} 行"
fi

# ---------- 2. 展示两个表 ----------
step "2. 两张表长什么样"
echo
echo "  表 emp（17 列）："
${MYSQL} -t -e "USE ssm_emp; DESCRIBE emp;" 2>/dev/null | head -20
echo
echo "  表 sys_dept（8 列）："
${MYSQL} -t -e "USE ssm_emp; DESCRIBE sys_dept;" 2>/dev/null | head -12

# ---------- 3. 跑程序 ----------
step "3. 运行程序（演示 CRUD / 动态 SQL / 分页 / 统计 / 逻辑删除）"
echo
mvn -q -DskipTests install >/dev/null 2>&1
mvn -q -pl lab-bootstrap exec:java 2>&1 | grep -vE "^\[INFO\]|^\[WARNING\]|SLF4J"

# ---------- 4. 跑测试 ----------
step "4. 跑全部测试"
mvn test 2>&1 | grep -E "Tests run:.*in com\.|Tests run: [0-9]+, Failures|BUILD"

# ---------- 5. git ----------
step "5. 提交记录"
git --no-pager log --oneline --decorate | head -15
echo
echo "  共 $(git rev-list --count HEAD) 次提交"
git --no-pager log -1 --pretty=format:"  最新：%h  %ad  %s%n" --date=format:"%Y-%m-%d %H:%M"

echo
say "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
say "  演示结束。"
say "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
