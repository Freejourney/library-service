# E-Library MVP — curl 场景测试文档

> 覆盖所有 API 场景的 curl 调用与预期结果。Base URL 按服务端口区分：
> - account: `http://localhost:8081`
> - catalog: `http://localhost:8082`
> - lending: `http://localhost:8083`
>
> 认证：`Authorization: Bearer <JWT>`；写操作需 `Idempotency-Key` 请求头。

## 0. 准备

### 0.1 测试数据

| 标识 | 类型 | 说明 |
|---|---|---|
| `U1` `...0001` | 账号 | NORMAL，可借 |
| `U2` `...0002` | 账号 | FROZEN，冻结 |
| `U3` `...0003` | 账号 | NORMAL，有逾期借阅 |
| `U4` `...0004` | 账号 | NORMAL，已借满 5 本 |
| `B1` `...000B` | 条目 | ON_SHELF，3 本可借 |
| `B2` `...000C` | 条目 | ON_SHELF，0 本（无库存） |
| `B3` `...000D` | 条目 | OFF_SHELF（下架） |
| `B4` `...000E` | 条目 | REMOVED（移除） |
| `B5` `...000F` | 条目 | ON_SHELF，2 本可借（Science） |
| `B6` `...0016` | 条目 | ON_SHELF，1 本可借（幂等冲突测试专用） |
| `C1` `...000A` | 分类 | Fiction（ACTIVE） |
| `C2` `...000C` | 分类 | Science（ACTIVE） |
| `C3` `...000D` | 分类 | Disabled Cat（DISABLED） |

### 0.2 环境变量

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

---

## 1. 账号资格（account:8081）

### 1.1 正常账号 → 200，可借

```bash
curl -s $ACC/api/v1/accounts/me -H "Authorization: Bearer $TOKEN"
```

**预期**：`200`，`borrowingEligible: true`，`eligibilityReasons: []`，`activeLoanLimit: 5`

### 1.2 冻结账号 → 200，不可借

```bash
curl -s $ACC/api/v1/accounts/me -H "Authorization: Bearer $(U2_JWT)"
```

**预期**：`200`，`status: FROZEN`，`borrowingEligible: false`，`eligibilityReasons: ["FROZEN"]`

### 1.3 有逾期账号 → 200，不可借

```bash
curl -s $ACC/api/v1/accounts/me -H "Authorization: Bearer $(U3_JWT)"
```

**预期**：`200`，`borrowingEligible: false`，`eligibilityReasons: ["OVERDUE_LOANS_EXIST"]`

### 1.4 未认证 → 401

```bash
curl -s -o /dev/null -w "%{http_code}" $ACC/api/v1/accounts/me
```

**预期**：`401`，`code: UNAUTHENTICATED`

---

## 2. 目录（catalog:8082，匿名）

### 2.1 分类列表 → 200（仅含上架条目的 ACTIVE 分类）

```bash
curl -s $CAT/api/v1/categories
```

**预期**：`200`，含 Fiction、Science；**不含** Disabled Cat

### 2.2 检索：关键词 → 200

```bash
curl -s "$CAT/api/v1/books?q=quantum"
```

**预期**：`200`，命中 Quantum Physics（B5）

### 2.3 检索：关键词大小写/全半角不敏感 → 200

```bash
curl -s "$CAT/api/v1/books?q=QUANTUM"
```

**预期**：`200`，命中 B5（NFKC + 小写归一化）

### 2.4 检索：`%`/`_` 转义 → 200（不误匹配）

```bash
curl -s "$CAT/api/v1/books?q=%25"
```

**预期**：`200`，空结果（`%` 被转义，不匹配任何书）

### 2.5 检索：纯空白 → 200（视为空查询）

```bash
curl -s "$CAT/api/v1/books?q=%20%20"
```

**预期**：`200`，返回全部上架条目

### 2.6 检索：关键词超 100 字符 → 400

```bash
curl -s "$CAT/api/v1/books?q=$(python3 -c 'print("a"*101)')"
```

**预期**：`400`，`code: INVALID_QUERY`

### 2.7 筛选：分类 → 200

```bash
curl -s "$CAT/api/v1/books?categoryId=$C2"
```

**预期**：`200`，仅 Science 分类条目

### 2.8 筛选：type → 200

```bash
curl -s "$CAT/api/v1/books?type=PERIODICAL"
```

**预期**：`200`，仅期刊（B3 下架不返回 → 空）

### 2.9 筛选：availability=true → 200（仅可借）

```bash
curl -s "$CAT/api/v1/books?availability=true"
```

**预期**：`200`，不含 B2（无库存）

### 2.10 排序：title_asc / title_desc → 200

```bash
curl -s "$CAT/api/v1/books?sort=title_asc"
curl -s "$CAT/api/v1/books?sort=title_desc"
```

**预期**：`200`，标题升/降序

### 2.11 分页：pageSize 超 100 → 400

```bash
curl -s "$CAT/api/v1/books?pageSize=101"
```

**预期**：`400`，`code: INVALID_REQUEST`

### 2.12 分页：页码越界 → 200（返回最后有效页）

```bash
curl -s "$CAT/api/v1/books?page=999&pageSize=20"
```

**预期**：`200`，返回末页（非空）

### 2.13 详情：上架条目 → 200（含 loanPolicy）

```bash
curl -s $CAT/api/v1/books/$B1
```

**预期**：`200`，`availableCount: 3`，`availability: AVAILABLE`，`loanPolicy.loanDays: 14`

### 2.14 详情：无库存条目 → 200（UNAVAILABLE）

```bash
curl -s $CAT/api/v1/books/$B2
```

**预期**：`200`，`availability: UNAVAILABLE`，`availableCount: 0`

### 2.15 详情：下架条目 → 404

```bash
curl -s -o /dev/null -w "%{http_code}" $CAT/api/v1/books/$B3
```

**预期**：`404`，`code: BOOK_NOT_FOUND`

### 2.16 详情：移除条目 → 404

```bash
curl -s -o /dev/null -w "%{http_code}" $CAT/api/v1/books/$B4
```

**预期**：`404`，`code: BOOK_NOT_FOUND`

### 2.17 详情：不存在 ID → 404

```bash
curl -s -o /dev/null -w "%{http_code}" $CAT/api/v1/books/00000000-0000-0000-0000-0000000000ff
```

**预期**：`404`，`code: BOOK_NOT_FOUND`

### 2.18 详情：非法 ID → 400

```bash
curl -s -o /dev/null -w "%{http_code}" $CAT/api/v1/books/not-a-uuid
```

**预期**：`400`，`code: INVALID_ID`

---

## 3. 借阅（lending:8083）

### 3.1 正常借阅 → 201

```bash
curl -s -X POST $LEN/api/v1/loans \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: borrow-b1-$(date +%s)" \
  -d "{\"catalogItemId\":\"$B1\"}"
```

**预期**：`201`，`status: ACTIVE`，`activeLoanCount: 1`，`maxActiveLoans: 5`；库存 3→2

### 3.2 幂等重放（同 key 同载荷）→ 201 + Idempotent-Replay

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

### 3.3 同 key 异载荷 → 409

```bash
KEY="borrow-conflict"
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" \
  -d "{\"catalogItemId\":\"$B6\"}" -o /dev/null -w "%{http_code}\n"
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" \
  -d "{\"catalogItemId\":\"$B5\"}" -w "\n%{http_code}\n"
```

**预期**：第一次 `201`（借 B6）；第二次 `409`，`code: IDEMPOTENCY_KEY_REUSED`（同 key 不同载荷）

### 3.4 缺 Idempotency-Key → 400

```bash
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"catalogItemId\":\"$B1\"}"
```

**预期**：`400`，`code: INVALID_IDEMPOTENCY_KEY`

### 3.5 冻结账号借阅 → 403

```bash
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $(U2_JWT)" \
  -H "Content-Type: application/json" -H "Idempotency-Key: u2-borrow" \
  -d "{\"catalogItemId\":\"$B1\"}"
```

**预期**：`403`，`code: ACCOUNT_NOT_ELIGIBLE`

### 3.6 有逾期账号借阅 → 409

```bash
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $(U3_JWT)" \
  -H "Content-Type: application/json" -H "Idempotency-Key: u3-borrow" \
  -d "{\"catalogItemId\":\"$B1\"}"
```

**预期**：`409`，`code: OVERDUE_LOANS_EXIST`

### 3.7 已借满账号借阅 → 409

```bash
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $(U4_JWT)" \
  -H "Content-Type: application/json" -H "Idempotency-Key: u4-borrow" \
  -d "{\"catalogItemId\":\"$B5\"}"
```

**预期**：`409`，`code: LOAN_LIMIT_EXCEEDED`

### 3.8 无库存条目借阅 → 409

```bash
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: borrow-b2" \
  -d "{\"catalogItemId\":\"$B2\"}"
```

**预期**：`409`，`code: BOOK_UNAVAILABLE`

### 3.9 下架条目借阅 → 409

```bash
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: borrow-b3" \
  -d "{\"catalogItemId\":\"$B3\"}"
```

**预期**：`409`，`code: BOOK_UNAVAILABLE`

### 3.10 移除条目借阅 → 404

```bash
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: borrow-b4" \
  -d "{\"catalogItemId\":\"$B4\"}"
```

**预期**：`404`，`code: BOOK_NOT_FOUND`

### 3.11 重复借同一条目 → 409

```bash
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: borrow-b5-1" \
  -d "{\"catalogItemId\":\"$B5\"}" -o /dev/null -w "%{http_code}\n"
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: borrow-b5-2" \
  -d "{\"catalogItemId\":\"$B5\"}" -w "\n%{http_code}\n"
```

**预期**：第一次 `201`；第二次 `409`，`code: DUPLICATE_ACTIVE_LOAN`

### 3.12 非法条目 ID → 400

```bash
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: borrow-bad" \
  -d '{"catalogItemId":"not-a-uuid"}'
```

**预期**：`400`，`code: INVALID_ID`

### 3.13 空请求体 → 400

```bash
curl -s -X POST $LEN/api/v1/loans -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: borrow-empty" \
  -d '{}'
```

**预期**：`400`，`code: INVALID_REQUEST`

---

## 4. 归还（lending:8083）

### 4.1 正常归还 → 200

```bash
LOAN_ID=<3.1 返回的 id>
curl -s -X POST $LEN/api/v1/loans/$LOAN_ID/return \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: return-$LOAN_ID"
```

**预期**：`200`，`status: RETURNED`，`availableCount` 回补 +1

### 4.2 重复归还 → 409

```bash
curl -s -X POST $LEN/api/v1/loans/$LOAN_ID/return \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: return-again-$LOAN_ID"
```

**预期**：`409`，`code: LOAN_ALREADY_RETURNED`

### 4.3 归还他人借阅 → 404

```bash
curl -s -X POST $LEN/api/v1/loans/$LOAN_ID/return \
  -H "Authorization: Bearer $(U2_JWT)" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: return-other"
```

**预期**：`404`，`code: LOAN_NOT_FOUND`（隐藏非本人）

### 4.4 逾期归还 → 200 RETURNED_LATE

```bash
# U3 的逾期借阅（loan_id ...0031）
curl -s -X POST $LEN/api/v1/loans/00000000-0000-0000-0000-000000000031/return \
  -H "Authorization: Bearer $(U3_JWT)" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: return-u3-overdue"
```

**预期**：`200`，`status: RETURNED_LATE`

### 4.5 归还不存在借阅 → 404

```bash
curl -s -X POST $LEN/api/v1/loans/00000000-0000-0000-0000-0000000000ff/return \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: return-nonexist"
```

**预期**：`404`，`code: LOAN_NOT_FOUND`

---

## 5. 个人借阅记录（lending:8083）

### 5.1 本人列表 → 200

```bash
curl -s "$LEN/api/v1/users/$U1/loans" -H "Authorization: Bearer $TOKEN"
```

**预期**：`200`，`items` 含本人借阅，`total` 正确

### 5.2 状态筛选 ACTIVE → 200

```bash
curl -s "$LEN/api/v1/users/$U1/loans?status=ACTIVE" -H "Authorization: Bearer $TOKEN"
```

**预期**：`200`，仅未归还且未逾期

### 5.3 状态筛选 OVERDUE → 200

```bash
curl -s "$LEN/api/v1/users/$U3/loans?status=OVERDUE" -H "Authorization: Bearer $(U3_JWT)"
```

**预期**：`200`，含逾期借阅，`dayCountType: OVERDUE_DAYS`

### 5.4 状态筛选 COMPLETED → 200

```bash
curl -s "$LEN/api/v1/users/$U1/loans?status=COMPLETED" -H "Authorization: Bearer $TOKEN"
```

**预期**：`200`，仅已归还

### 5.5 非法状态 → 400

```bash
curl -s "$LEN/api/v1/users/$U1/loans?status=BOGUS" -H "Authorization: Bearer $TOKEN"
```

**预期**：`400`，`code: INVALID_REQUEST`

### 5.6 非本人 userId → 404

```bash
curl -s -o /dev/null -w "%{http_code}" "$LEN/api/v1/users/$U2/loans" -H "Authorization: Bearer $TOKEN"
```

**预期**：`404`，`code: LOAN_NOT_FOUND`（隐藏他人数据）

### 5.7 单条详情（本人）→ 200

```bash
curl -s "$LEN/api/v1/loans/$LOAN_ID" -H "Authorization: Bearer $TOKEN"
```

**预期**：`200`，含快照字段（title/creator/type）、`catalogStatus`

### 5.8 单条详情（非本人）→ 404

```bash
curl -s -o /dev/null -w "%{http_code}" "$LEN/api/v1/loans/$LOAN_ID" -H "Authorization: Bearer $(U2_JWT)"
```

**预期**：`404`，`code: LOAN_NOT_FOUND`

---

## 6. 幂等操作结果查询（lending:8083）

### 6.1 查询已记录操作 → 200

```bash
curl -s "$LEN/api/v1/loan-operations/borrow-b1-replay" -H "Authorization: Bearer $TOKEN"
```

**预期**：`200`，`state: COMPLETED`，`httpStatus: 201`，`result` 为原响应

### 6.2 查询未记录操作 → 404

```bash
curl -s -o /dev/null -w "%{http_code}" "$LEN/api/v1/loan-operations/nonexistent-key" -H "Authorization: Bearer $TOKEN"
```

**预期**：`404`，`code: OPERATION_NOT_FOUND`

### 6.3 查询他人操作 → 404

```bash
curl -s -o /dev/null -w "%{http_code}" "$LEN/api/v1/loan-operations/borrow-b1-replay" -H "Authorization: Bearer $(U2_JWT)"
```

**预期**：`404`（隐藏他人数据）

---

## 7. 错误响应格式（通用）

所有错误响应统一格式：

```json
{"code":"...","message":"...","details":{},"timestamp":"...","traceId":"..."}
```

- 不泄露 SQL、堆栈、Token。
- 业务拒绝（403/404/409）记 WARN 日志；依赖不可用（503）与未预期（500）记 ERROR 日志。
- 所有响应（含错误）带 `traceId`，与 `X-Trace-Id` 响应头一致。

---

## 8. 验证脚本

逐条执行并断言状态码的脚本见 `scripts/verify-scenarios.sh`。

### 8.1 前置条件

- 三个服务运行中（account:8081 / catalog:8082 / lending:8083）
- MySQL 有测试数据（`scripts/reset-test-data.sql` 可重置基线）
- JWT 已生成（`/tmp/jwt-u1.jwt` ~ `/tmp/jwt-u4.jwt`）
- JWK 服务器运行中（HTTPS :9443）

### 8.2 运行

```bash
# 1. 重置测试数据（每次运行前执行，脚本会改变数据库状态）
docker exec -i library-service-mysql-1 mysql -uelibrary -pelibrary-local elibrary_db < scripts/reset-test-data.sql

# 2. 运行验证
./scripts/verify-scenarios.sh
```

### 8.3 验证结果（2026-10-30）

**52/52 全部通过** ✅

| 章节 | 场景数 | 结果 |
|---|---|---|
| 1. 账号资格 | 4 | 全部 PASS |
| 2. 目录 | 18 | 全部 PASS |
| 3. 借阅 | 13 | 全部 PASS |
| 4. 归还 | 5 | 全部 PASS |
| 5. 个人借阅记录 | 8 | 全部 PASS |
| 6. 幂等操作查询 | 3 | 全部 PASS |

> 注：验证脚本会改变数据库状态（借阅/归还/幂等记录），重复运行前需先执行 `reset-test-data.sql` 重置基线。