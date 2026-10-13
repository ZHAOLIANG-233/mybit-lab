#!/usr/bin/env bash
# =====================================================================
# start-mysql.sh · 启动本工程自己的 MySQL 实例
#
# 数据放在工程内的 _runtime/mysql-lab-data，端口 3307，
# 不碰你本机已有的 3306 实例，删掉工程目录就彻底干净。
#
# 用法：
#   bash scripts/start-mysql.sh          # 启动（已在跑就跳过）
#   bash scripts/start-mysql.sh stop     # 停止
#   bash scripts/start-mysql.sh reset    # 停止并删除数据（下次会重建空库）
# =====================================================================
set -uo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MYSQLD="/opt/homebrew/opt/mysql/bin/mysqld"
BASE_DIR="/opt/homebrew/opt/mysql"
RUNTIME_DIR="${PROJECT_DIR}/_runtime"
DATA_DIR="${RUNTIME_DIR}/mysql-lab-data"
SOCKET="/tmp/emp-mybatis-3307.sock"
PID_FILE="${RUNTIME_DIR}/mysql-lab.pid"
ERR_LOG="${RUNTIME_DIR}/mysql-lab.err"
PORT=3307

# Homebrew 的 mysqld 位置可能不同，兜底找一下
if [ ! -x "${MYSQLD}" ]; then
    MYSQLD="$(command -v mysqld 2>/dev/null || true)"
    BASE_DIR="$(dirname "$(dirname "${MYSQLD}")")"
fi

running() {
    mysqladmin --protocol=TCP -h 127.0.0.1 -P ${PORT} -u root -p123456 ping >/dev/null 2>&1
}

case "${1:-start}" in
    stop)
        if [ -f "${PID_FILE}" ]; then
            kill "$(cat "${PID_FILE}")" 2>/dev/null && echo "已停止（端口 ${PORT}）"
            rm -f "${PID_FILE}"
        else
            echo "没发现 PID 文件，实例可能没在跑"
        fi
        exit 0
        ;;
    reset)
        [ -f "${PID_FILE}" ] && kill "$(cat "${PID_FILE}")" 2>/dev/null
        sleep 2
        rm -rf "${DATA_DIR}" && echo "数据已清空，下次启动会重建空库（记得重新导入 sql/）"
        exit 0
        ;;
esac

if running; then
    echo "MySQL 已在运行（127.0.0.1:${PORT}）"
    exit 0
fi

if [ -z "${MYSQLD}" ] || [ ! -x "${MYSQLD}" ]; then
    echo "找不到 mysqld，请先安装 MySQL：" >&2
    echo "  brew install mysql" >&2
    exit 1
fi

mkdir -p "${RUNTIME_DIR}"

if [ ! -d "${DATA_DIR}/mysql" ]; then
    echo "首次运行：初始化数据目录（约 10 秒）"
    mkdir -p "${DATA_DIR}"
    "${MYSQLD}" --initialize-insecure --datadir="${DATA_DIR}" --basedir="${BASE_DIR}" 2>/dev/null
    # 初始化后 root 无口令，启动后再设成 123456
    INIT_PASS=""
else
    INIT_PASS="-p123456"
fi

echo "启动 MySQL（127.0.0.1:${PORT}）..."
nohup "${MYSQLD}" \
    --datadir="${DATA_DIR}" \
    --basedir="${BASE_DIR}" \
    --port="${PORT}" \
    --socket="${SOCKET}" \
    --pid-file="${PID_FILE}" \
    --log-error="${ERR_LOG}" \
    --mysqlx=0 \
    >/dev/null 2>&1 &

for i in $(seq 1 30); do
    sleep 1
    if mysqladmin --protocol=TCP -h 127.0.0.1 -P ${PORT} -u root ${INIT_PASS} ping >/dev/null 2>&1; then
        if [ -z "${INIT_PASS}" ]; then
            mysql --protocol=TCP -h 127.0.0.1 -P ${PORT} -u root \
                -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '123456';" 2>/dev/null
        fi
        echo "启动完成：127.0.0.1:${PORT}"
        exit 0
    fi
done

echo "启动失败，看日志：${ERR_LOG}" >&2
exit 1
