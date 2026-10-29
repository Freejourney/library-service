# 电子图书馆服务 (E-Library) MVP 技术方案

> 目标：用最小的工程复杂度跑通全部核心业务——**账号资格查询、目录浏览/检索/详情、借阅、归还、个人借阅记录**。
> 不在范围：注册/登录/凭证管理（由 OIDC 提供方承接）、馆员后台与馆藏维护、通知、阅读器与正文访问、预约、续借、罚金。

---

## 1. MVP 范围与原则

MVP 采用模块化单体和单个 MySQL 数据库，覆盖核心业务：账号资格查询、目录浏览/检索/详情、借阅、归还和个人借阅记录。数据在同库事务内维护，保证借阅、库存和幂等结果一致。

本期不包含注册/登录/凭证管理（由 OIDC 提供方承接）、馆员后台与馆藏维护、通知、阅读器与正文访问、预约、续借和罚金。

### 核心业务约束

这些约束保证业务正确性，MVP 必须落实：

1. **借阅/归还强一致事务**：Loan、库存、幂等结果同事务提交。
2. **最后一个名额只有一人成功**：行锁 + 数据库唯一约束兜底。
3. **幂等**：`Idempotency-Key` + 请求摘要比对 + 结果查询接口。
4. **业务规则**：上限 5 本、无逾期、同书不可重复借、14 个本地自然日、23:59 应还。
5. **借阅快照**：目录下架/移除后仍可识别、归还。
6. **逾期读时派生**：不依赖定时任务改状态。
7. **统一响应与错误码**、可信身份（不信任请求体中的读者 ID）。

---

## 2. 架构总览

```text
读者端
  │  HTTPS (JWT)
  ▼
Nginx / 云 LB（TLS、限流、路由）
  │
  ▼
┌──────────────────────────────────────────────┐
│  elibrary-app（Java 21 + Spring Boot 3 单体） │
│  ┌──────────┐ ┌──────────┐ ┌───────────────┐ │
│  │ account  │ │ catalog  │ │   lending     │ │
│  │ module   │ │ module   │ │   module      │ │
│  └──────────┘ └──────────┘ └───────────────┘ │
│  OAuth2 Resource Server（校验 OIDC 签发的 JWT）│
└──────────────────────┬───────────────────────┘
                       ▼
                MySQL 8.4 / InnoDB（单库 elibrary_db）
```

### 模块职责与边界

- **account**：读取账号状态与借阅资格；账号的创建/冻结/注销由受信任渠道（运维脚本或内部管理工具直接调用应用服务/SQL）完成，不开放读者 API。
- **catalog**：分类、目录条目、搜索、详情、上下架状态。
- **lending**：借阅、归还、规则校验、幂等、个人借阅记录。

**模块间规则**：模块之间只通过 Java 接口（Application Service）调用，**不直接访问对方的 Repository/表**。这样将来需要拆服务时，沿模块边界切即可。

### 技术栈

| 项 | 选型 | 说明 |
|---|---|---|
| 语言/框架 | Java 21 + Spring Boot 3（Spring MVC） | I/O + 事务型业务，阻塞模型更直观 |
| 持久化 | MySQL 8.4 LTS + InnoDB + MyBatis | 借阅 SQL 显式控制 `FOR UPDATE` |
| 构建 | Gradle Wrapper | 固定 Gradle 构建版本 |
| 迁移 | Flyway | 禁止生产自动建表 |
| 认证 | Spring Security OAuth2 Resource Server | 校验 issuer/audience/exp，取 `sub` 作为 `accountId` |
| 校验/文档 | Bean Validation + OpenAPI 3 | |
| 可观测 | Spring Actuator + Micrometer/Prometheus；JSON 日志 + `traceId` | 保留最小闭环 |
| 测试 | JUnit 5 + Testcontainers（MySQL 8.4） | **并发与锁测试必须用真实 MySQL，不用 H2** |

### 通用约定

- 时间：数据库存 UTC `DATETIME(6)`，连接会话时区 `+00:00`；应用层用 `Instant`；业务日期按配置的馆区时区（如 `Asia/Shanghai`）计算；API 返回带偏移量的 RFC 3339。
- 主键：UUIDv7（`BINARY(16)`）。
- 事务隔离级别：`READ COMMITTED`，显式配置。
- 字符集：`utf8mb4` / `utf8mb4_0900_ai_ci`。

---

## 3. 数据模型

仅包含支撑 MVP 业务所需的数据表。

```text
accounts(account_id PK, status, eligibility_level, ...)
categories(category_id PK, name, status, ...)
catalog_items(item_id PK, category_id FK, shelf_status, total_copies, available_copies, search_text, ...)
loans(loan_id PK, account_id, item_id, status, borrowed_at, due_at, returned_at, 快照字段 ...)
idempotency_records(account_id, operation, idempotency_key, request_hash, http_status, response_json, ...)
```

### DDL

```sql
CREATE DATABASE elibrary_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- ===== account =====
CREATE TABLE accounts (
  account_id BINARY(16) NOT NULL,
  status VARCHAR(32) NOT NULL,                       -- NORMAL/FROZEN/PENDING_VERIFICATION/DELETED
  eligibility_level VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
  display_name VARCHAR(160) NULL,
  version BIGINT UNSIGNED NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (account_id),
  CONSTRAINT ck_account_status CHECK
    (status IN ('NORMAL','FROZEN','PENDING_VERIFICATION','DELETED'))
) ENGINE=InnoDB;

-- ===== catalog =====
CREATE TABLE categories (
  category_id BINARY(16) NOT NULL,
  name VARCHAR(120) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',      -- ACTIVE/DISABLED
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (category_id),
  UNIQUE KEY uk_category_name (name),
  CONSTRAINT ck_category_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB;

CREATE TABLE catalog_items (
  item_id BINARY(16) NOT NULL,
  type VARCHAR(24) NOT NULL,                         -- EBOOK/PERIODICAL
  title VARCHAR(512) NOT NULL,
  creator VARCHAR(512) NULL,
  category_id BINARY(16) NOT NULL,
  subject_keywords JSON NULL,
  description TEXT NULL,
  publisher VARCHAR(255) NULL,
  publication_date DATE NULL,
  language_code VARCHAR(16) NULL,
  identifier_type VARCHAR(24) NULL,                  -- ISBN / ISSUE 等
  identifier_value VARCHAR(128) NULL,
  metadata JSON NULL,                                -- 类型特有属性（期刊卷期等）
  cover_url VARCHAR(1024) NULL,
  search_text VARCHAR(2048) NOT NULL,                -- NFKC+小写后的 标题+作者+主题，应用层写入
  shelf_status VARCHAR(32) NOT NULL,                 -- ON_SHELF/OFF_SHELF/REMOVED
  total_copies INT UNSIGNED NOT NULL DEFAULT 0,
  available_copies INT UNSIGNED NOT NULL DEFAULT 0,
  published_at DATETIME(6) NULL,
  version BIGINT UNSIGNED NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (item_id),
  KEY ix_item_category_status (category_id, shelf_status, item_id),
  KEY ix_item_status_published (shelf_status, published_at, title(100), item_id),
  CONSTRAINT fk_item_category FOREIGN KEY (category_id) REFERENCES categories(category_id),
  CONSTRAINT ck_item_type CHECK (type IN ('EBOOK','PERIODICAL')),
  CONSTRAINT ck_item_shelf CHECK (shelf_status IN ('ON_SHELF','OFF_SHELF','REMOVED')),
  CONSTRAINT ck_item_copies CHECK (available_copies <= total_copies)
) ENGINE=InnoDB;

-- ===== lending =====
CREATE TABLE loans (
  loan_id BINARY(16) NOT NULL,
  account_id BINARY(16) NOT NULL,
  item_id BINARY(16) NOT NULL,
  status VARCHAR(24) NOT NULL,                       -- ACTIVE/RETURNED/RETURNED_LATE（OVERDUE 读时派生）
  borrowed_at DATETIME(6) NOT NULL,
  due_at DATETIME(6) NOT NULL,
  returned_at DATETIME(6) NULL,
  -- 仅对"有效借阅"生效的唯一约束（MySQL 无部分索引，用生成列 + NULL 不冲突实现）
  active_account_id BINARY(16) GENERATED ALWAYS AS
    (IF(returned_at IS NULL, account_id, NULL)) STORED,
  title_snapshot VARCHAR(512) NOT NULL,
  creator_snapshot VARCHAR(512) NULL,
  type_snapshot VARCHAR(32) NOT NULL,
  policy_version VARCHAR(64) NOT NULL,
  version BIGINT UNSIGNED NOT NULL DEFAULT 1,
  PRIMARY KEY (loan_id),
  UNIQUE KEY uk_active_account_item (active_account_id, item_id),
  KEY ix_account_due (account_id, returned_at, due_at, loan_id),
  KEY ix_account_returned (account_id, returned_at, loan_id),
  CONSTRAINT ck_loan_time CHECK (due_at > borrowed_at),
  CONSTRAINT ck_loan_state CHECK
    ((returned_at IS NULL AND status = 'ACTIVE') OR
     (returned_at IS NOT NULL AND status IN ('RETURNED','RETURNED_LATE')))
) ENGINE=InnoDB;
-- 注意：loans.item_id 不加外键，避免目录永久移除/维护时受限；历史靠快照字段识别。

CREATE TABLE idempotency_records (
  account_id BINARY(16) NOT NULL,
  operation VARCHAR(64) NOT NULL,                    -- BORROW / RETURN:{loanId}
  idempotency_key VARCHAR(128) NOT NULL,
  request_hash BINARY(32) NOT NULL,
  http_status SMALLINT UNSIGNED NOT NULL,
  response_json JSON NOT NULL,
  created_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,                   -- 建议 7 天，配置化
  PRIMARY KEY (account_id, operation, idempotency_key),
  KEY ix_idem_expiry (expires_at)
) ENGINE=InnoDB;
```

### 数据模型约束

- **去掉 `loan_slots`**：用 `available_copies` 计数替代逐名额行。借出 `-1`、归还 `+1`，同事务内由 `catalog_items` 行锁保护；`CHECK (available_copies <= total_copies)` 兜底。
- **去掉 `uk_active_slot`**：不再有 Slot；"同一读者对同一书只能有一条有效借阅"由 `uk_active_account_item` 兜底。
- **去掉所有投影/租约/Outbox/Inbox/资产表。**
- **幂等表只存终态结果**：并发相同 key 时，第二个请求会在唯一索引上等待第一个提交，随后读取已存结果，因此无需 `PROCESSING` 状态。

---

## 4. 核心业务设计

### 4.1 借阅策略（配置化）

| 参数 | 默认值 |
|---|---|
| 同时有效借阅上限 | 5 |
| 借期 | 14 个本地自然日（借阅当天算第 1 日） |
| 应还时刻 | 到期日 23:59:00 |
| 馆区时区 | `Asia/Shanghai`（IANA ZoneId） |
| `policy_version` | 随每笔 Loan 保存，配置变更不影响已借记录 |

**应还时间算法**：取借阅时间在馆区时区的本地日期 `D`，应还时刻 = `D + 13 天` 的 `23:59:00`，再按同一 ZoneId 转回 `Instant`。使用本地日历加日，天然正确处理月末、闰日、夏令时。

**到期判定**：`now < dueAt` 为按时归还；`now >= dueAt` 为逾期（`RETURNED_LATE`）。

### 4.2 状态模型

```text
CatalogItem.shelf_status:  ON_SHELF ⇄ OFF_SHELF；REMOVED（终态）
Loan:  ACTIVE ──(return, now < dueAt)────────────▶ RETURNED
       ACTIVE ──(return, now >= dueAt)───────────▶ RETURNED_LATE
       ACTIVE ──(读时：now >= dueAt 且未归还)─────▶ 展示为 OVERDUE（不落库）
```

- 未归还且已过 `due_at` 的借阅展示为 `OVERDUE`，并**阻止新增借阅**。
- 账号冻结/注销/待核验：禁止新借，已有借阅仍可归还。
- 下架/移除条目：禁止新借，已有借阅仍可归还。

### 4.3 借阅流程 `POST /api/v1/loans`

所有步骤在**一个本地事务**内完成，不再有跨服务调用：

```text
1. 从 JWT 取 accountId；校验 Idempotency-Key、请求体（catalogItemId）
2. 开启事务（READ COMMITTED）
3. 幂等：INSERT idempotency_records 占位前，先 SELECT 查已有记录
     - 已存在且 request_hash 相同 → 直接重放原 http_status/response（Header: Idempotent-Replay: true）
     - 已存在但 hash 不同       → 409 IDEMPOTENCY_KEY_REUSED
4. 锁读者：SELECT * FROM accounts WHERE account_id=? FOR UPDATE
     - 不存在 → 404 ACCOUNT_NOT_FOUND
     - status != NORMAL → 403 ACCOUNT_NOT_ELIGIBLE
5. 校验该读者规则（均在读者行锁保护下，同一读者串行）：
     - 存在 returned_at IS NULL AND due_at <= now 的借阅 → 409 OVERDUE_LOANS_EXIST
     - 有效借阅数 >= 5                                   → 409 LOAN_LIMIT_EXCEEDED
     - 已存在同条目有效借阅                               → 409 DUPLICATE_ACTIVE_LOAN
6. 锁条目：SELECT ... FROM catalog_items WHERE item_id=? FOR UPDATE
     - 不存在/REMOVED → 404 BOOK_NOT_FOUND
     - shelf_status != ON_SHELF 或 available_copies = 0 → 409 BOOK_UNAVAILABLE
7. 计算 dueAt；INSERT loans（含 title/creator/type 快照、policy_version）
8. UPDATE catalog_items SET available_copies = available_copies - 1, version = version + 1
9. INSERT idempotency_records（终态 201 + 响应体）
10. COMMIT → 返回 201 Loan
```

要点：

- **加锁顺序固定为 账号 → 条目**，借阅与归还路径一致，避免死锁；遇到死锁（`1213`）做有限次重试（建议 ≤3 次）。
- **最后名额竞争**：多个读者同时借同一本，条目行锁串行化，第一个提交者成功，其余读到 `available_copies = 0` 返回 `BOOK_UNAVAILABLE`。
- **同一读者并发借不同书**：账号行锁串行，保证不会突破 5 本上限。
- **上下架竞态**：下架操作需对 `catalog_items` 行加锁更新；借阅事务持有该行锁，二者互斥，因此不需要 BorrowLease。
- **业务规则拒绝** → 整体回滚，不产生 Loan，不改库存。是否记录被拒结果：MVP 不记录，重试时重新评估（规则确定，结果一致）。
- **响应丢失**：客户端用同一 key 重试 POST，或调用 `GET /loan-operations/{key}` 取回原结果。

### 4.4 归还流程 `POST /api/v1/loans/{id}/return`

```text
1. 取 accountId；校验 Idempotency-Key
2. 开启事务；幂等检查（同借阅流程）
3. SELECT * FROM loans WHERE loan_id=? AND account_id=? FOR UPDATE
     - 不存在或非本人 → 404 LOAN_NOT_FOUND（统一隐藏，防探测）
     - returned_at 非空 → 409 LOAN_ALREADY_RETURNED
4. SELECT ... FROM catalog_items WHERE item_id=? FOR UPDATE   -- 条目仍存在则加锁
5. now < due_at → RETURNED；否则 RETURNED_LATE；写 returned_at、status、version
6. 若条目存在：UPDATE catalog_items SET available_copies = available_copies + 1
     （条目已永久移除的，跳过库存回补）
7. 写幂等结果；COMMIT → 200
```

- 并发双归还：由 Loan 行锁串行，只有第一个成功，第二个得到 `409`，库存不会重复回补。
- 下架/移除条目仍可归还（靠 Loan 快照，不依赖目录可见性）。

### 4.5 目录检索

- **搜索列**：写入/更新条目时，应用层把 `title + creator + subject_keywords` 做 **Unicode NFKC 规范化 + 小写（case fold）** 后写入 `search_text`。
- **查询**：`q` 先 trim，纯空白视为空，最多 100 个 Unicode 字符，超过返回 `400 INVALID_QUERY`（不截断）；同样做 NFKC + 小写后，对 `search_text` 执行 `LIKE CONCAT('%', ?, '%')`，**对 `%`、`_`、`\` 先转义**。
- **筛选**：`categoryId`、`type`、`availability`（`available_copies > 0`）取交集；只返回 `ON_SHELF`。
- **排序**：默认 `published_at DESC, title ASC, item_id ASC`；可选 `title_asc|title_desc`、`publishedAt_desc`；始终追加 `item_id` 作为唯一次级键，保证分页稳定。
- **分页**：页码从 1；默认 20，最大 100；超过末页返回最后有效页；无结果返回第 1 页空列表。
- **可借数**：直接读 `available_copies`，是实时值。借阅是否成功仍以事务内检查为准。
- **性能说明**：`LIKE '%x%'` 无法使用索引，属全扫描。MVP 阶段书目量级（建议 ≲ 10 万条）可接受；超出后见"演进路线"。

### 4.6 个人借阅记录

- `GET /users/{userId}/loans`：`userId` 必须等于 JWT subject，否则 `404`（不暴露用户存在性）。
- 状态筛选：
  - `ACTIVE`：`returned_at IS NULL AND due_at > now`
  - `OVERDUE`：`returned_at IS NULL AND due_at <= now`
  - `COMPLETED`：`returned_at IS NOT NULL`
- 排序：有效记录 `due_at ASC, loan_id ASC`；完成记录 `returned_at DESC, loan_id ASC`；未传状态时按分组展示。
- 剩余/逾期天数按馆区本地自然日计算；未到期但不足 24 小时返回 `IMMINENT`；刚到期返回 `OVERDUE_DAYS, days=0`。
- 展示字段用 Loan 快照；`catalogStatus` 通过 `LEFT JOIN catalog_items` 取当前状态（条目已移除则为 `REMOVED`）。
- 借还完成后个人列表直接读主库，天然 read-your-writes。

---

## 5. API 清单

Base Path：`/api/v1`；JSON UTF-8。共 9 个对外接口。

| # | Method / Path | 认证 | 用途 |
|---|---|---|---|
| 1 | `GET /accounts/me` | 必须 | 本人账号状态与借阅资格 |
| 2 | `GET /categories` | 匿名 | 有上架条目的分类列表 |
| 3 | `GET /books` | 匿名 | 检索/筛选/分页目录 |
| 4 | `GET /books/{id}` | 匿名 | 图书详情（含借阅规则、可借数） |
| 5 | `POST /loans` | 必须 | 借阅（需 `Idempotency-Key`） |
| 6 | `POST /loans/{id}/return` | 必须 | 归还（需 `Idempotency-Key`） |
| 7 | `GET /users/{userId}/loans` | 必须且本人 | 个人借阅列表 |
| 8 | `GET /loans/{id}` | 必须且本人 | 单条借阅详情/历史快照 |
| 9 | `GET /loan-operations/{idempotencyKey}` | 必须且本人 | 网络结果未知时查询操作结果 |

运维端点（仅内网）：`/actuator/health/liveness`、`/actuator/health/readiness`、`/actuator/prometheus`。

### 通用规范

- 成功响应：`{code, message, data, timestamp, traceId}`；列表 `data` 为 `{items, page, pageSize, total, sort}`。
- 错误响应：`{code, message, details, timestamp, traceId}`；不泄露 SQL、堆栈、Token 或他人数据。
- 读者身份只从 JWT `sub` 获取，**不信任**请求体/路径中的读者 ID（`/users/{userId}/loans` 仅做一致性校验）。
- 同 key + 同请求摘要 → 重放原结果；同 key + 不同摘要 → `409 IDEMPOTENCY_KEY_REUSED`。

### 关键接口契约

**GET /accounts/me** → `200`
```json
{"code":"OK","message":"success","data":{"accountId":"usr_01","status":"NORMAL","borrowingEligible":true,"eligibilityReasons":[],"activeLoanLimit":5}}
```
冻结/待核验/注销账号仍返回 `200`，`borrowingEligible=false`；无业务账号 `404 ACCOUNT_NOT_FOUND`。

**GET /books/{id}** → `200`：含 `title/creator/category/description/publisher/publicationDate/language/identifier/coverUrl/availableCount/availability/loanPolicy{loanDays,dueTime,timeZone,maxActiveLoans,renewal:"NOT_SUPPORTED"}`。无库存仍 `200`（`UNAVAILABLE`）；下架/移除/不存在 → `404 BOOK_NOT_FOUND`。

**POST /loans**
```json
// Request
{"catalogItemId":"bk_01"}
// 201 Response data
{"id":"loan_01","catalogItemId":"bk_01","title":"示例书","creator":"作者甲","type":"EBOOK",
 "borrowedAt":"2026-10-29T10:00:00+08:00","dueAt":"2026-11-11T23:59:00+08:00",
 "status":"ACTIVE","activeLoanCount":1,"maxActiveLoans":5}
```

**POST /loans/{id}/return** → `200`：`{id, status: RETURNED|RETURNED_LATE, returnedAt, catalogItemId, availableCount, activeLoanCount}`。

**GET /loan-operations/{key}** → `200 {state: COMPLETED, httpStatus, result}`；无记录 `404 OPERATION_NOT_FOUND`。

### 错误码

| 错误码 | HTTP | 含义 |
|---|---:|---|
| `INVALID_REQUEST` / `INVALID_QUERY` / `INVALID_ID` / `INVALID_IDEMPOTENCY_KEY` | 400 | 参数、分页、排序、关键词（>100 字符）等无效 |
| `UNAUTHENTICATED` | 401 | 缺少/无效/过期 Token |
| `ACCOUNT_NOT_ELIGIBLE` | 403 | 账号冻结、注销或未核验 |
| `ACCOUNT_NOT_FOUND` / `BOOK_NOT_FOUND` / `LOAN_NOT_FOUND` / `OPERATION_NOT_FOUND` | 404 | 资源不存在；非本人记录统一隐藏为 404 |
| `OVERDUE_LOANS_EXIST` | 409 | 存在未归还逾期项 |
| `LOAN_LIMIT_EXCEEDED` | 409 | 已持有 5 项有效借阅 |
| `DUPLICATE_ACTIVE_LOAN` | 409 | 已有效借阅同一条目 |
| `BOOK_UNAVAILABLE` | 409 | 已下架、无可用名额或最后名额已被取走 |
| `LOAN_ALREADY_RETURNED` / `LOAN_NOT_RETURNABLE` | 409 | 已完成或当前状态不可归还 |
| `IDEMPOTENCY_KEY_REUSED` | 409 | 相同 key 对应不同请求内容 |
| `DEPENDENCY_UNAVAILABLE` | 503 | 数据库等不可用 |
| `INTERNAL_ERROR` | 500 | 未预期错误 |


---

## 6. 代码结构

单仓库、单可部署产物，按**业务能力**分包，每个模块内部沿用 Clean Architecture 分层：

```text
elibrary-app/
  src/main/java/com/example/elibrary/
    account/
      api/            # AccountController、DTO
      application/    # GetMyAccount、CheckBorrowingEligibility（供 lending 调用的接口）
      domain/         # ReaderAccount、AccountStatus
      infrastructure/ # MyBatis Mapper、Repository 实现
    catalog/
      api/            # BookController、CategoryController
      application/    # SearchBooks、GetBook、ListCategories、LockItemForLending（供 lending 调用）
      domain/         # CatalogItem、SearchCriteria
      infrastructure/ # MyBatis Mapper、Repository 实现、搜索文本规范化（NFKC）
    lending/
      api/            # LoanController、OperationController
      application/    # BorrowLoan、ReturnLoan、ListReaderLoans、GetLoan、GetOperation
      domain/         # Loan、LoanPolicy、DueDateCalculator、领域异常
      infrastructure/ # MyBatis Mapper、LoanRepository、IdempotencyRepository
    common/           # 统一响应/异常、ErrorCode、分页、Clock、TraceId、安全上下文
    config/           # Security(JWT)、Clock/ZoneId、LoanPolicyProperties、事务隔离级别
  src/main/resources/db/migration/   # Flyway：V1__init.sql ...
  build.gradle                       # Gradle 构建配置
  Dockerfile                         # 应用容器镜像
  compose.yaml                       # 本地 app + MySQL 环境
  src/test/java/.../{domain,application,integration}
```

约定：

- Domain 不依赖 Spring；Controller DTO 与领域对象分离。
- `lending` 通过 `account.application` / `catalog.application` 暴露的接口读取账号状态和锁定条目，不直接拼对方的表（同库但保持模块边界）。
- `BorrowLoan`、`ReturnLoan` 各是一个 `@Transactional` 用例；提交前任何异常即整体回滚，**不要吞异常后继续提交**。
- `Clock` 与 `ZoneId` 注入，便于测试固定时间。
- 不为简单 CRUD 引入过度抽象。

---

## 7. 安全与可观测（最小集）

**安全**
- JWT 校验 issuer / audience / exp / 签名；`sub` 即 `accountId`。
- 反向代理做限流与 TLS；actuator 仅内网可达。
- 数据库账号最小权限；密钥由 Secret 注入，不入库不入代码。
- 日志不记录 Token、完整个人信息；用户 ID 如需关联使用受控散列。

**可观测**
- JSON 结构化日志：`timestamp, level, service, traceId, requestId, userIdHash, operation, catalogItemId/loanId, resultCode, durationMs`。业务拒绝记 WARN，不记 ERROR。
- 指标（Micrometer → Prometheus）：借还成功/拒绝计数（按错误码）、事务耗时、锁等待/死锁次数、API 延迟分位数、连接池使用率、当前逾期借阅数。避免 `userId` 等高基数标签。
- `traceId` 写入响应体、响应头与日志。

**数据生命周期**
- 借阅历史长期保留，用户不可修改/删除；账号注销后借阅历史按隐私政策去标识化，不破坏归还与审计关联。
- 幂等记录保留 7 天（配置化），到期由定时任务清理，**不删除 Loan**。
- MySQL 每日全量备份 + binlog（支持 PITR），定期做恢复演练。

---

## 8. 测试策略

**原则**：核心借还路径用 **Testcontainers MySQL 8.4** 做集成测试；固定 Clock `2026-10-29T10:00:00+08:00`、馆区 `Asia/Shanghai`。

| 层 | 重点 |
|---|---|
| Domain 单元 | 应还时间（跨月/跨年/闰日/夏令时、23:59:00）、`now == dueAt` 属逾期、逾期派生、规则与状态转换的正向/拒绝/边界 |
| Application 用例 | 步骤顺序、幂等重放、错误映射、失败回滚 |
| 集成（真实 MySQL） | 生成列唯一约束、行锁语义、最后名额 N 路并发仅 1 成功、同读者并发借不同书不超 5 本、同读者同书并发只 1 条、双归还只回补一次、失败注入时 Loan 与库存同回滚 |
| API 契约 | 9 个接口的状态码 + 错误码 + 关键字段，错误响应无敏感信息泄露 |
| E2E 主链路 | 搜索 → 详情 → 借阅 → 超时用同 key 重试/查询 → 个人列表 → 到期（改 Clock）→ 逾期归还 → 历史；另覆盖"下架后仍可归还""最后名额竞争" |

测试数据：`U1` 正常、`U2` 冻结、`U3` 有逾期、`U4` 已借满 5 本；`B1` 上架有 1 本、`B0` 上架无库存、`BO` 下架、`BR` 已移除。

必测边界摘要：关键词 100/101 字符、纯空白、全半角与大小写、`%`/`_` 转义；筛选交集；页码越界；排序稳定；4 本可借/5 本拒绝；一项逾期即拒；同条目重复借拒绝、归还后可重借；同 key 同载荷重放、同 key 异载荷 409、响应丢失后重试不重复借；非本人 Loan 一律 404。

---

## 9. 交付与部署

- **构建**：Gradle Wrapper 构建 Spring Boot 可执行 JAR，并打包为 Docker 镜像。
- **运行环境**：Docker；本地使用 Docker Compose 启动 app + MySQL，生产环境以 Docker 容器运行至少 2 个应用实例，并连接 MySQL 主备及 Nginx/LB。
- **配置项**：JWT issuer/audience/JWK 地址、借阅策略（上限、天数、应还时刻、时区）、幂等保留天数、DB 连接、限流阈值。
- **发布门禁**：单元 + 集成（含并发）测试通过、Flyway 迁移可重复执行、健康检查就绪、冒烟用例通过。
- **初始数据**：通过 Flyway 种子脚本或运维导入工具写入分类、目录条目（含 `total_copies/available_copies`）和读者账号。本期不提供馆员维护界面。


---

## 10. 演进路线（MVP 之后按需触发）

MVP 的模块边界与表设计已为下列演进预留空间；**只有出现对应瓶颈时才引入**，避免过早复杂化。

| 触发条件 | 演进动作 |
|---|---|
| 书目量级增长，`LIKE` 检索变慢 | 先加 MySQL FULLTEXT（ngram）；再不够才引入 OpenSearch，由目录变更事件更新索引 |
| 单应用/单库吞吐不足 | 应用水平扩容 → 读副本承接目录读 → 垂直扩容；借还仍走主库 |
| 需要馆员维护目录/名额 | 增加 catalog 管理 API；需要逐名额管理时再引入 `loan_slots` 表 |
| 需要阅读/下载正文 | 引入对象存储 + `book_assets` + 短时签名 URL + CDN，新增 `content-access` 接口，授权依赖有效 Loan |
| 团队/模块独立发布需求 | 沿 account / catalog / lending 模块边界拆服务，再按需引入 Outbox、消息队列与资格投影 |
| 多馆/多租户 | 增加 `library_id`，按馆分库；保证账号、目录库存、借阅同馆同库 |
| 历史借阅数据量大 | 按时间归档冷历史表；有效借阅始终留主表 |
| 通知、预约、罚金、续借 | 作为新模块/事件消费者接入，不改动借还核心事务 |

---

