# E-Library MVP — 微服务架构

电子图书馆服务 MVP。**account / catalog / lending 三个独立 Spring Boot 服务**共享一个 `common` 工程，单 MySQL 数据库由 Flyway 管埋。读者身份取自已校验 JWT 的 `sub` 声明。

## 架构

```text
读者端
  │  HTTPS (JWT)
  ▼
┌──────────────────────────────────────────────────────┐
│  account:8081    catalog:8082    lending:8083        │
│  (Spring Boot 3 / Java 21 独立进程)                   │
└───────────────────────┬──────────────────────────────┘
                        ▼
                 MySQL 8.4 (elibrary_db)
```

- **common** — 共享契约与基础：统一响应/异常/分页、JWT 资源服务器、时钟、Flyway 迁移、借阅策略配置（`elibrary.loan.*`）、MyBatis UUID 处理器。
- **account** — 账号资格查询 `GET /accounts/me`；账号创建/冻结/注销由受信任渠道完成，不开放读者 API。
- **catalog** — 目录：分类、检索、详情 `GET /categories`、`GET /books`、`GET /books/{id}`。
- **lending** — 借阅/归还/个人记录/幂等结果：`POST /loans`、`POST /loans/{id}/return`、`GET /users/{userId}/loans`、`GET /loans/{id}`、`GET /loan-operations/{key}`。

模块间只通过 Java 接口调用（`AccountAccessPort`、`CatalogReadPort`、`CatalogInventoryPort`、`LoanCountPort`、`LendingPolicyPort`）；沿模块边界即可拆分为独立部署单元。

## 快速开始

### 1. 仅数据库用 Docker（推荐本地开发）

```bash
docker compose up -d mysql        # 启动 MySQL 8.4（端口 3306）
```

### 2. 本地启动三个服务

```bash
export DB_URL='jdbc:mysql://127.0.0.1:3306/elibrary_db?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
export DB_USERNAME=elibrary DB_PASSWORD=elibrary-local
export JWT_ISSUER_URI=https://issuer.example.com JWT_AUDIENCE=elibrary-api

./gradlew :account:bootRun --args='--server.port=8081'   # account  :8081
./gradlew :catalog:bootRun --args='--server.port=8082'   # catalog  :8082
./gradlew :lending:bootRun --args='--server.port=8083'   # lending  :8083
```

健康检查：`curl localhost:8081/actuator/health/readiness`（catalog/lending 同理）。

### 3. 全部用 Docker 启动（镜像构建较慢）

```bash
docker compose up --build        # mysql + account + catalog + lending
```

## 配置项

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/elibrary_db?...` | 数据库连接（UTC 会话时区） |
| `DB_USERNAME` / `DB_PASSWORD` | `elibrary` / `elibrary-local` | 数据库凭据（仅本地默认值） |
| `JWT_ISSUER_URI` | `https://issuer.example.com` | OIDC issuer，用于签名校验 |
| `JWT_JWK_SET_URI` | 空 | 固定 JWK 端点（无 issuer 发现时） |
| `JWT_AUDIENCE` | `elibrary-api` | 期望的 audience |
| `LOAN_MAX_ACTIVE` | `5` | 同时有效借阅上限 |
| `LOAN_DAYS` | `14` | 借期（本地自然日） |
| `LOAN_DUE_TIME` | `23:59:00` | 应还时刻 |
| `LIBRARY_ZONE` | `Asia/Shanghai` | 馆区时区 |
| `LOAN_POLICY_VERSION` | `v1` | 随 Loan 落库的策略版本 |
| `IDEMPOTENCY_RETENTION_DAYS` | `7` | 幂等记录保留天数 |

## 测试

```bash
./gradlew test        # 单元测试 + Testcontainers 集成测试（无 Docker 时自动跳过）

# 或在容器内跑指定服务测试（可选）
SERVICE=lending docker compose -f compose.test.yaml run --rm test
```

## 构建

```bash
./gradlew :account:bootJar :catalog:bootJar :lending:bootJar
# 产出 account/build/libs/account-app.jar、catalog-app.jar、lending-app.jar

# 单服务镜像
docker build --build-arg SERVICE=account -t elibrary-account .
```

- Flyway 迁移：`common/src/main/resources/db/migration/`（禁止生产自动建表）。
- 认证：Spring Security OAuth2 Resource Server，校验 issuer/audience/exp/签名；`sub` 即 `accountId`；不信任请求体中的读者 ID。
- 幂等：`Idempotency-Key` + 请求摘要比对；`GET /loan-operations/{key}` 取回原结果。

## API 清单（Base Path `/api/v1`）

| Method / Path | 认证 | 服务 | 用途 |
|---|---|---|---|
| `GET /accounts/me` | 必须 | account | 本人账号状态与借阅资格 |
| `GET /categories` | 匿名 | catalog | 有上架条目的分类列表 |
| `GET /books` | 匿名 | catalog | 检索/筛选/分页目录 |
| `GET /books/{id}` | 匿名 | catalog | 图书详情 |
| `POST /loans` | 必须 | lending | 借阅（需 `Idempotency-Key`） |
| `POST /loans/{id}/return` | 必须 | lending | 归还（需 `Idempotency-Key`） |
| `GET /users/{userId}/loans` | 必须且本人 | lending | 个人借阅列表 |
| `GET /loans/{id}` | 必须且本人 | lending | 单条借阅详情 |
| `GET /loan-operations/{idempotencyKey}` | 必须且本人 | lending | 查询幂等操作结果 |

完整业务规则、错误码与接口契约见 [tech-mvp.md](./tech-mvp.md)。

## 已验证的端到端链路

本地启动（MySQL 在 Docker，服务在 JVM）后已验证：

1. `GET /accounts/me` → 账号状态与借阅资格（`borrowingEligible`）
2. `POST /loans` → 借阅成功，库存 `available_copies` 递减
3. 同 `Idempotency-Key` 重放 → `201` + `Idempotent-Replay: true`，不重复借阅
4. `GET /users/{userId}/loans` → 个人记录（馆区时区、剩余/逾期天数）
5. `POST /loans/{id}/return` → 归还，库存回补

> 本地联调 JWT：使用自签名 JWK 端点时，需将证书导入 JVM 信任库
> （`keytool -import -trustcacerts -alias <name> -file <cert.pem> -keystore $JAVA_HOME/lib/security/cacerts`）。
