#!/usr/bin/env bash
# 一键启动本地开发环境：MySQL + JWK 服务器 + 证书导入 + 测试 JWT 签发
# 用法: ./scripts/setup-local.sh
set -euo pipefail

cd "$(dirname "$0")/.."

# Docker CLI 可能不在 PATH（macOS Docker.app）
export PATH="/Applications/Docker.app/Contents/Resources/bin:$PATH"

echo "==> 1/4 启动基础设施（MySQL + JWK 服务器）"
docker compose up -d mysql jwk

echo "==> 2/4 等待 JWK 服务器就绪"
for i in $(seq 1 30); do
  if curl -sk https://localhost:9443/jwks >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
curl -sk https://localhost:9443/jwks >/dev/null 2>&1 || { echo "JWK 服务器未就绪"; exit 1; }

echo "==> 3/4 导入证书到 JVM 信任库"
# 检测 JAVA_HOME：环境变量 > java_home 工具 > mise > 常见路径
if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in \
    "$(command -v /usr/libexec/java_home >/dev/null 2>&1 && /usr/libexec/java_home 2>/dev/null || true)" \
    "$HOME/.local/share/mise/installs/java/openjdk-21.0.2" \
    "$HOME/.sdkman/candidates/java/current" \
    "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"; do
    if [ -n "$candidate" ] && [ -f "$candidate/lib/security/cacerts" ]; then
      JAVA_HOME="$candidate"
      break
    fi
  done
fi
if [ -z "${JAVA_HOME:-}" ] || [ ! -f "$JAVA_HOME/lib/security/cacerts" ]; then
  echo "警告: 未找到 JVM cacerts，跳过证书导入（服务将无法校验 HTTPS JWK）"
else
  KEYSTORE="$JAVA_HOME/lib/security/cacerts"
  keytool -delete -alias elibrary-jwk -keystore "$KEYSTORE" -storepass changeit -noprompt 2>/dev/null || true
  keytool -import -trustcacerts -alias elibrary-jwk -file docker/jwk/cert.pem \
    -keystore "$KEYSTORE" -storepass changeit -noprompt
  echo "证书已导入: $KEYSTORE"
fi

echo "==> 4/4 签发测试 JWT"
for name in u1 u2 u3 u4; do
  curl -sk "https://localhost:9443/token?sub=00000000-0000-0000-0000-00000000000${name#u}" \
    | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])" > "/tmp/jwt-${name}.jwt"
done
# 兼容旧引用 /tmp/test.jwt
cp /tmp/jwt-u1.jwt /tmp/test.jwt

echo ""
echo "✅ 本地环境就绪"
echo "   MySQL:  localhost:3306 (elibrary/elibrary-local)"
echo "   JWK:    https://localhost:9443/jwks"
echo "   Token:  https://localhost:9443/token (默认 sub=U1)"
echo "   JWT:    /tmp/jwt-u1.jwt ~ /tmp/jwt-u4.jwt"
echo ""
echo "启动服务:"
echo "   export DB_URL='jdbc:mysql://127.0.0.1:3306/elibrary_db?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'"
echo "   export DB_USERNAME=elibrary DB_PASSWORD=elibrary-local"
echo "   export JWT_ISSUER_URI=https://issuer.example.com JWT_AUDIENCE=elibrary-api"
echo "   export JWT_JWK_SET_URI=https://localhost:9443/jwks"
echo "   ./gradlew :account:bootRun --args='--server.port=8081'"
echo "   ./gradlew :catalog:bootRun --args='--server.port=8082'"
echo "   ./gradlew :lending:bootRun --args='--server.port=8083'"