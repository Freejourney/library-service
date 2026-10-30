# E-Library MVP 验收文档（Validation）

> 本文档提供**本地启动步骤**与**全场景 curl 测试命令及预期结果**，用于验收 E-Library MVP。
> 覆盖需求：浏览书籍、查询书籍详细资料、借阅书籍、归还书籍、查看目前已借阅的书籍。

---

## 1. Quickstart（本地启动）

### 前置要求

| 依赖 | 版本 | 说明 |
|---|---|---|
| JDK | 21 | 构建与运行三个服务 |
| Docker | 任意 | 运行 MySQL 与 JWK 服务器 |
| Gradle | 8.10.2（wrapper） | 项目自带 `./gradlew` |

### 步骤 1：一键启动基础设施

```bash
./scripts/setup-local.sh
```

脚本自动完成：
1. 启动 MySQL 8.4（`localhost:3306`，账号 `elibrary`/`elibrary-local`）
2. 启动 JWK 服务器（`https://localhost:9443`，模拟 OIDC Provider）
3. 导入自签名证书到 JVM 信任库
4. 签发测试 JWT 到 `/tmp/jwt-u1.jwt` ~ `/tmp/jwt-u4.jwt`（及 `/tmp/test.jwt`）

> 也可手动：`docker compose up -d mysql jwk` + `keytool` 导入证书 + `curl -sk https://localhost:9443/token` 签发 JWT。

### 步骤 2：重置测试数据

```bash
docker exec -i library-service-mysql-1 mysql -uelibrary -pelibrary-local elibrary_db \
  < scripts/reset-test-data.sql
```

基线数据：4 个账号（U1 正常 / U2 冻结 / U3 有逾期 / U4 借满）、9 个条目（B1 可借 / B2 无库存 / B3 下架 / B4 移除 / B5 可借 / B6 幂等测试）、3 个分类。

### 步骤 3：启动三个服务

```bash
./scripts/start-services.sh
```

脚本自动完成：
1. 构建三个服务的 jar（`bootJar`）
2. **检测端口占用**：若服务已在运行（8081/8082/8083 被占用），先停止旧进程再重启
3. 启动三个服务（日志在 `/tmp/elibrary-{account,catalog,lending}.log`）
4. 等待就绪并输出健康检查结果

> 环境变量默认值已内置（`DB_URL`/`DB_USERNAME`/`DB_PASSWORD`/`JWT_*`），如需覆盖可先 `export` 再运行脚本。
> 也可手动启动：

```bash
export DB_URL='jdbc:mysql://127.0.0.1:3306/elibrary_db?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
export DB_USERNAME=elibrary DB_PASSWORD=elibrary-local
export JWT_ISSUER_URI=https://issuer.example.com JWT_AUDIENCE=elibrary-api
export JWT_JWK_SET_URI=https://localhost:9443/jwks

./gradlew :account:bootRun --args='--server.port=8081'   # account  :8081
./gradlew :catalog:bootRun --args='--server.port=8082'   # catalog  :8082
./gradlew :lending:bootRun --args='--server.port=8083'   # lending  :8083
```

### 步骤 4：健康检查

```bash
curl -s localhost:8081/actuator/health/readiness   # {"status":"UP"}
curl -s localhost:8082/actuator/health/readiness   # {"status":"UP"}
curl -s localhost:8083/actuator/health/readiness   # {"status":"UP"}
```

> 若使用 `start-services.sh`，脚本末尾已自动输出健康检查结果，可跳过本步骤。

### 步骤 5：准备环境变量

```bash
export TOKEN=$(cat /tmp/test.jwt)          # U1 的 JWT
export ACC=http://localhost:8081           # account
export CAT=http://localhost:8082           # catalog
export LEN=http://localhost:8083           # lending
export U1=00000000-0000-0000-0000-000000000001
export U2=00000000-0000-0000-0000-000000000002
export U3=00000000-0000-0000-0000-000000000003
export U4=00000000-0000-0000-0000-000000000004
export B1=00000000-0000-0000-0000-00000000000b
export B2=00000000-0000-0000-0000-00000000000c
export B3=00000000-0000-0000-0000-00000000000d
export B4=00000000-0000-0000-0000-00000000000e
export B5=00000000-0000-0000-0000-00000000000f
export B6=00000000-0000-0000-0000-000000000016
```

> 各账号 JWT：`/tmp/jwt-u1.jwt`（U1）、`/tmp/jwt-u2.jwt`（U2）、`/tmp/jwt-u3.jwt`（U3）、`/tmp/jwt-u4.jwt`（U4）。
> 以下命令中 `Authorization: Bearer $TOKEN` 为 U1；其他账号用对应 JWT 文件。

---

## 2. 场景测试（curl + 预期结果）

### 2.1 账号资格（account:8081）

#### 2.1.1 正常账号 → 200，可借

```bash
curl -s $ACC/api/v1/accounts/me -H "Authorization: Bearer $TOKEN"
```

**预期**：`200`，`borrowingEligible: true`，`eligibilityReasons: []`，`activeLoanLimit: 5`

```json
{"code":"OK","data":{"accountId":"...0001","status":"NORMAL","borrowingEligible":true,"eligibilityReasons":[],"activeLoanCount":0,"activeLoanLimit":5}}
```

#### 2.1.2 冻结账号 → 200，不可借

```bash
curl -s $ACC/api/v1/accounts/me -H "Authorization: Bearer $(cat /tmp/jwt-u2.jwt)"
```

**预期**：`200`，`status: FROZEN`，`borrowingEligible: false`，`eligibilityReasons: ["FROZEN"]`

#### 2.1.3 有逾期账号 → 200，不可借

```bash
curl -s $ACC/api/v1/accounts/me -H "Authorization: Bearer $(cat /tmp/jwt-u3.jwt)"
```

**预期**：`200`，`borrowingEligible: false`，`eligibilityReasons: ["OVERDUE_LOANS_EXIST"]`

#### 2.1.4 未认证 → 401

```bash
curl -s -o /dev/null -w "%{http_code}" $ACC/api/v1/accounts/me
```

**预期**：`401`，`code: UNAUTHENTICATED`

---

### 2.2 目录浏览与检索（catalog:8082，匿名）

#### 2.2.1 分类列表 → 200

```bash
curl -s $CAT/api/v1/categories
```

**预期**：`200`，仅含上架条目的 ACTIVE 分类（Fiction、Science），不含 DISABLED 分类

#### 2.2.2 关键词检索 → 200

```bash
curl -s "$CAT/api/v1/books?q=quantum"
```

**预期**：`200`，`total: 1`，命中 "Quantum Physics"

#### 2.2.3 大小写不敏感 → 200

```bash
curl -s "$CAT/api/v1/books?q=QUANTUM"
```

**预期**：`200`，`total: 1`（大小写不敏感）

#### 2.2.4 `%` 转义 → 200（不误匹配）

```bash
curl -s "$CAT/api/v1/books?q=%25"
```

**预期**：`200`，`total: 0`（`%` 被转义，不匹配任何条目）

#### 2.2.5 纯空白 → 200（视为空查询）

```bash
curl -s "$CAT/api/v1/books?q=%20%20"
```

**预期**：`200`，返回全部上架条目（`total: 8`：B1/B2/B5/B6/B10/B11/B12/B13）

#### 2.2.6 关键词超 100 字符 → 400

```bash
curl -s "$CAT/api/v1/books?q=$(python3 -c 'print("a"*101)')"
```

**预期**：`400`，`code: INVALID_QUERY`

#### 2.2.7 分类筛选 → 200

```bash
curl -s "$CAT/api/v1/books?categoryId=$C2"
```

**预期**：`200`，仅 Science 分类条目（`total: 1`，命中 "Quantum Physics"）

#### 2.2.8 type 筛选 → 200

```bash
curl -s "$CAT/api/v1/books?type=PERIODICAL"
```

**预期**：`200`，仅 PERIODICAL 类型（上架的无，`total: 0`）

#### 2.2.9 availability=true → 200（仅可借）

```bash
curl -s "$CAT/api/v1/books?availability=true"
```

**预期**：`200`，仅 `available_copies > 0` 的条目（`total: 3`：B1/B5/B6）

#### 2.2.10 排序 title_asc / title_desc → 200

```bash
curl -s "$CAT/api/v1/books?sort=title_asc"
curl -s "$CAT/api/v1/books?sort=title_desc"
```

**预期**：`200`，分别按标题升序/降序

#### 2.2.11 pageSize 超 100 → 400

```bash
curl -s "$CAT/api/v1/books?pageSize=101"
```

**预期**：`400`，`code: INVALID_REQUEST`

#### 2.2.12 页码越界 → 200（收敛到最后一页）

```bash
curl -s "$CAT/api/v1/books?page=999"
```

**预期**：`200`，返回最后有效页

---

### 2.3 书籍详情（catalog:8082，匿名）

#### 2.3.1 上架条目 → 200（含 loanPolicy）

```bash
curl -s $CAT/api/v1/books/$B1
```

**预期**：`200`，含书目元数据、`availability: AVAILABLE`、`loanPolicy`（loanDays/dueTime/maxActiveLoans）

#### 2.3.2 无库存条目 → 200（UNAVAILABLE）

```bash
curl -s $CAT/api/v1/books/$B2
```

**预期**：`200`，`availability: UNAVAILABLE`，`availableCount: 0`

#### 2.3.3 下架条目 → 404

```bash
curl -s -o /dev/null -w "%{http_code}" $CAT/api/v1/books/$B3
```

**预期**：`404`，`code: BOOK_NOT_FOUND`

#### 2.3.4 移除条目 → 404

```bash
curl -s -o /dev/null -w "%{http_code}" $CAT/api/v1/books/$B4
```

**预期**：`404`，`code: BOOK_NOT_FOUND`

#### 2.3.5 不存在 ID → 404

```bash
curl -s -o /dev/null -w "%{http_code}" $CAT/api/v1/books/00000000-0000-0000-0000-0000000000ff
```

**预期**：`404`，`code: BOOK_NOT_FOUND`

#### 2.3.6 非法 ID → 400

```bash
curl -s -o /dev/null -w "%{http_code}" $CAT/api/v1/books/not-a-uuid
```

**预期**：`400`，`code: INVALID_ID`

---

### 2.4 借阅（lending:8083）

#### 2.4.1 正常借阅 → 201

```bash
curl -s -X POST $LEN/api/v1/loans \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: borrow-b1-$(date +%s)" \
  -d "{\"catalogItemId\":\"$B1\"}"
```

**预期**：`201`，`status: ACTIVE`，`activeLoanCount: 1`，`maxActiveLoans: 5`；库存 3→2

```json
{"code":"OK","data":{"id":"<uuid>","catalogItemId":"...000b","title":"The Great Book","status":"ACTIVE","activeLoanCount":1,"maxActiveLoans":5}}
```

#### 2.4.2 幂等重放（同 key 同载荷）→ 201 + Idempotent-Replay

```bash
KEY="borrow-b1-replay"
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" \
  -d "{\"catalogItemId\":\"$B1\"}" -D - -o /dev/null | grep -i "HTTP\|Idempotent-Replay"
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" \
  -d "{\"catalogItemId\":\"$B1\"}" -D - -o /dev/null | grep -i "HTTP\|Idempotent-Replay"
```

**预期**：第一次 `201`；第二次 `201` + `Idempotent-Replay: true`，不重复借阅

#### 2.4.3 同 key 异载荷 → 409

```bash
KEY="borrow-conflict"
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" \
  -d "{\"catalogItemId\":\"$B6\"}" -o /dev/null -w "%{http_code}\n"
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" \
  -d "{\"catalogItemId\":\"$B5\"}" -w "\n%{http_code}\n"
```

**预期**：第一次 `201`（借 B6）；第二次 `409`，`code: IDEMPOTENCY_KEY_REUSED`

#### 2.4.4 缺 Idempotency-Key → 400

```bash
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"catalogItemId\":\"$B1\"}"
```

**预期**：`400`，`code: INVALID_IDEMPOTENCY_KEY`

#### 2.4.5 冻结账号借阅 → 403

```bash
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $(cat /tmp/jwt-u2.jwt)" \
  -H "Content-Type: application/json" -H "Idempotency-Key: u2-borrow" \
  -d "{\"catalogItemId\":\"$B1\"}"
```

**预期**：`403`，`code: ACCOUNT_NOT_ELIGIBLE`

#### 2.4.6 有逾期账号借阅 → 409

```bash
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $(cat /tmp/jwt-u3.jwt)" \
  -H "Content-Type: application/json" -H "Idempotency-Key: u3-borrow" \
  -d "{\"catalogItemId\":\"$B1\"}"
```

**预期**：`409`，`code: OVERDUE_LOANS_EXIST`

#### 2.4.7 已借满账号借阅 → 409

```bash
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $(cat /tmp/jwt-u4.jwt)" \
  -H "Content-Type: application/json" -H "Idempotency-Key: u4-borrow" \
  -d "{\"catalogItemId\":\"$B5\"}"
```

**预期**：`409`，`code: LOAN_LIMIT_EXCEEDED`

#### 2.4.8 无库存条目借阅 → 409

```bash
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: borrow-b2" \
  -d "{\"catalogItemId\":\"$B2\"}"
```

**预期**：`409`，`code: BOOK_UNAVAILABLE`

#### 2.4.9 下架条目借阅 → 409

```bash
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: borrow-b3" \
  -d "{\"catalogItemId\":\"$B3\"}"
```

**预期**：`409`，`code: BOOK_UNAVAILABLE`

#### 2.4.10 移除条目借阅 → 404

```bash
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: borrow-b4" \
  -d "{\"catalogItemId\":\"$B4\"}"
```

**预期**：`404`，`code: BOOK_NOT_FOUND`

#### 2.4.11 重复借同一条目 → 409

```bash
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: borrow-b5-1" \
  -d "{\"catalogItemId\":\"$B5\"}" -o /dev/null -w "%{http_code}\n"
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: borrow-b5-2" \
  -d "{\"catalogItemId\":\"$B5\"}" -w "\n%{http_code}\n"
```

**预期**：第一次 `201`；第二次 `409`，`code: DUPLICATE_ACTIVE_LOAN`

#### 2.4.12 非法条目 ID → 400

```bash
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: borrow-bad" \
  -d '{"catalogItemId":"not-a-uuid"}'
```

**预期**：`400`，`code: INVALID_ID`

#### 2.4.13 空请求体 → 400

```bash
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: borrow-empty" \
  -d '{}'
```

**预期**：`400`，`code: INVALID_REQUEST`

---

### 2.5 归还（lending:8083）

> 先执行 2.4.1 获取 `LOAN_ID`（响应 `data.id`）。

#### 2.5.1 正常归还 → 200

```bash
LOAN_ID=<2.4.1 返回的 id>
curl -s -X POST $LEN/api/v1/loans/$LOAN_ID/return \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: return-$LOAN_ID"
```

**预期**：`200`，`status: RETURNED`，`availableCount` 回补 +1

```json
{"code":"OK","data":{"id":"<uuid>","status":"RETURNED","returnedAt":"...","availableCount":3}}
```

#### 2.5.2 重复归还 → 409

```bash
curl -s -X POST $LEN/api/v1/loans/$LOAN_ID/return \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: return-again-$LOAN_ID"
```

**预期**：`409`，`code: LOAN_ALREADY_RETURNED`

#### 2.5.3 归还他人借阅 → 404

```bash
curl -s -X POST $LEN/api/v1/loans/$LOAN_ID/return \
  -H "Authorization: Bearer $(cat /tmp/jwt-u2.jwt)" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: return-other"
```

**预期**：`404`，`code: LOAN_NOT_FOUND`（隐藏非本人）

#### 2.5.4 逾期归还 → 200 RETURNED_LATE

```bash
# U3 的逾期借阅（loan_id ...0031）
curl -s -X POST $LEN/api/v1/loans/00000000-0000-0000-0000-000000000031/return \
  -H "Authorization: Bearer $(cat /tmp/jwt-u3.jwt)" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: return-u3-overdue"
```

**预期**：`200`，`status: RETURNED_LATE`

#### 2.5.5 归还不存在借阅 → 404

```bash
curl -s -X POST $LEN/api/v1/loans/00000000-0000-0000-0000-0000000000ff/return \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: return-nonexist"
```

**预期**：`404`，`code: LOAN_NOT_FOUND`

---

### 2.6 个人借阅记录（lending:8083）

#### 2.6.1 本人列表 → 200

```bash
curl -s "$LEN/api/v1/users/$U1/loans" -H "Authorization: Bearer $TOKEN"
```

**预期**：`200`，`items` 含本人借阅，`total` 正确

#### 2.6.2 状态筛选 ACTIVE → 200

```bash
curl -s "$LEN/api/v1/users/$U1/loans?status=ACTIVE" -H "Authorization: Bearer $TOKEN"
```

**预期**：`200`，仅未归还且未逾期

#### 2.6.3 状态筛选 OVERDUE → 200

```bash
curl -s "$LEN/api/v1/users/$U3/loans?status=OVERDUE" -H "Authorization: Bearer $(cat /tmp/jwt-u3.jwt)"
```

**预期**：`200`，含逾期借阅，`dayCountType: OVERDUE_DAYS`

#### 2.6.4 状态筛选 COMPLETED → 200

```bash
curl -s "$LEN/api/v1/users/$U1/loans?status=COMPLETED" -H "Authorization: Bearer $TOKEN"
```

**预期**：`200`，仅已归还

#### 2.6.5 非法状态 → 400

```bash
curl -s "$LEN/api/v1/users/$U1/loans?status=BOGUS" -H "Authorization: Bearer $TOKEN"
```

**预期**：`400`，`code: INVALID_REQUEST`

#### 2.6.6 非本人 userId → 404

```bash
curl -s -o /dev/null -w "%{http_code}" "$LEN/api/v1/users/$U2/loans" -H "Authorization: Bearer $TOKEN"
```

**预期**：`404`，`code: LOAN_NOT_FOUND`（隐藏他人数据）

#### 2.6.7 单条详情（本人）→ 200

```bash
curl -s "$LEN/api/v1/loans/$LOAN_ID" -H "Authorization: Bearer $TOKEN"
```

**预期**：`200`，含快照字段（title/creator/type）、`catalogStatus`

#### 2.6.8 单条详情（非本人）→ 404

```bash
curl -s -o /dev/null -w "%{http_code}" "$LEN/api/v1/loans/$LOAN_ID" -H "Authorization: Bearer $(cat /tmp/jwt-u2.jwt)"
```

**预期**：`404`，`code: LOAN_NOT_FOUND`

---

### 2.7 幂等操作结果查询（lending:8083）

#### 2.7.1 查询已记录操作 → 200

```bash
curl -s "$LEN/api/v1/loan-operations/borrow-b1-replay" -H "Authorization: Bearer $TOKEN"
```

**预期**：`200`，`state: COMPLETED`，`httpStatus: 201`，`result` 为原响应

#### 2.7.2 查询未记录操作 → 404

```bash
curl -s -o /dev/null -w "%{http_code}" "$LEN/api/v1/loan-operations/nonexistent-key" -H "Authorization: Bearer $TOKEN"
```

**预期**：`404`，`code: OPERATION_NOT_FOUND`

#### 2.7.3 查询他人操作 → 404

```bash
curl -s -o /dev/null -w "%{http_code}" "$LEN/api/v1/loan-operations/borrow-b1-replay" -H "Authorization: Bearer $(cat /tmp/jwt-u2.jwt)"
```

**预期**：`404`（隐藏他人数据）

---

## 3. 错误响应格式（通用）

所有错误响应统一格式：

```json
{"code":"...","message":"...","details":{},"timestamp":"...","traceId":"..."}
```

- 不泄露 SQL、堆栈、Token。
- 业务拒绝（403/404/409）记 WARN 日志；依赖不可用（503）与未预期（500）记 ERROR 日志。
- 所有响应（含错误）带 `traceId`，与 `X-Trace-Id` 响应头一致。

---

## 4. 自动验证（可选）

逐条断言状态码的脚本见 `scripts/verify-scenarios.sh`（52 个场景，已全部验证通过）：

```bash
# 1. 重置测试数据
docker exec -i library-service-mysql-1 mysql -uelibrary -pelibrary-local elibrary_db \
  < scripts/reset-test-data.sql

# 2. 运行验证（需三服务运行中 + JWT + JWK 服务器）
./scripts/verify-scenarios.sh
```

> 注意：场景测试会改变数据库状态（借阅/归还/幂等记录），重复运行前需先执行 `reset-test-data.sql` 重置基线。

---

## 5. 停止所有服务

验收完成后停止全部服务与容器：

```bash
# 1. 停止三个服务（JVM 进程）
pkill -f "account-app.jar|catalog-app.jar|lending-app.jar" 2>/dev/null || true

# 2. 停止并移除容器（MySQL + JWK；数据卷保留，下次启动数据仍在）
docker compose down

# 3. 如需彻底清空数据（删除数据卷，下次启动为全新库）
# docker compose down -v
```

> 停止后端口 8081/8082/8083/9443/3306 全部释放。下次验收只需重新执行
> `./scripts/setup-local.sh` + `./scripts/start-services.sh` 即可。