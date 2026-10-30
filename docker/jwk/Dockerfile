# 本地联调 JWK/JWT 服务器镜像：HTTPS 暴露 jwks.json 与 /token 签发接口
# 仅用于本地开发，生产环境由真实 OIDC Provider 提供 JWK 端点
FROM python:3.12-slim

RUN pip install --no-cache-dir cryptography

WORKDIR /app
COPY jwk_server.py jwks.json cert.pem key.pem /app/

EXPOSE 9443
ENTRYPOINT ["python3", "/app/jwk_server.py"]
