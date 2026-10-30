#!/usr/bin/env bash
# 启动三个服务：若端口已被占用（服务已在运行）则先停止再重启
# 用法: ./scripts/start-services.sh
set -euo pipefail

cd "$(dirname "$0")/.."

# Docker CLI 可能不在 PATH（macOS Docker.app）
export PATH="/Applications/Docker.app/Contents/Resources/bin:$PATH"

# 默认环境变量（可被外部覆盖）
export DB_URL="${DB_URL:-jdbc:mysql://127.0.0.1:3306/elibrary_db?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true}"
export DB_USERNAME="${DB_USERNAME:-elibrary}"
export DB_PASSWORD="${DB_PASSWORD:-elibrary-local}"
export JWT_ISSUER_URI="${JWT_ISSUER_URI:-https://issuer.example.com}"
export JWT_JWK_SET_URI="${JWT_JWK_SET_URI:-https://localhost:9443/jwks}"
export JWT_AUDIENCE="${JWT_AUDIENCE:-elibrary-api}"

JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
  JAVA_BIN="$(command -v java || true)"
fi
if [ -z "$JAVA_BIN" ]; then
  echo "错误: 未找到 java，请设置 JAVA_HOME" >&2
  exit 1
fi

stop_if_running() { # stop_if_running <port> <name>
  local port="$1" name="$2"
  if lsof -i ":$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "==> $name 已在运行(:$port)，停止旧进程"
    local pids
    pids=$(lsof -ti ":$port" -sTCP:LISTEN 2>/dev/null || true)
    for pid in $pids; do
      kill "$pid" 2>/dev/null || true
    done
    # 等待端口释放
    for _ in $(seq 1 15); do
      if ! lsof -i ":$port" -sTCP:LISTEN >/dev/null 2>&1; then
        break
      fi
      sleep 1
    done
  fi
}

start_service() { # start_service <jar> <port> <name>
  local jar="$1" port="$2" name="$3"
  echo "==> 启动 $name(:$port)"
  nohup "$JAVA_BIN" -jar "$jar" --server.port="$port" \
    > "/tmp/elibrary-${name}.log" 2>&1 &
  echo "    pid=$!"
}

# 先构建（确保 jar 最新）
echo "==> 构建服务 jar"
./gradlew :account:bootJar :catalog:bootJar :lending:bootJar --console=plain -q

stop_if_running 8081 account
stop_if_running 8082 catalog
stop_if_running 8083 lending

start_service account/build/libs/account-app.jar 8081 account
sleep 2
start_service catalog/build/libs/catalog-app.jar 8082 catalog
sleep 2
start_service lending/build/libs/lending-app.jar 8083 lending

echo ""
echo "==> 等待服务就绪"
for port in 8081 8082 8083; do
  for _ in $(seq 1 30); do
    if curl -s -o /dev/null "http://localhost:$port/actuator/health/readiness" 2>/dev/null; then
      break
    fi
    sleep 1
  done
  code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$port/actuator/health/readiness" 2>/dev/null || echo 000)
  echo "   :$port -> $code"
done

echo ""
echo "✅ 三个服务已就绪"
echo "   account :8081  catalog :8082  lending :8083"
echo "   日志: /tmp/elibrary-account.log /tmp/elibrary-catalog.log /tmp/elibrary-lending.log"