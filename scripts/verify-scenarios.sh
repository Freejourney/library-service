#!/usr/bin/env bash
# E-Library MVP 场景验证脚本
# 前置：三个服务运行中(8081/8082/8083)、MySQL 有测试数据、/tmp/jwt-{u1..u4}.jwt 存在
set -u

ACC=http://localhost:8081
CAT=http://localhost:8082
LEN=http://localhost:8083

T1=$(cat /tmp/jwt-u1.jwt)
T2=$(cat /tmp/jwt-u2.jwt)
T3=$(cat /tmp/jwt-u3.jwt)
T4=$(cat /tmp/jwt-u4.jwt)

U1=00000000-0000-0000-0000-000000000001
U2=00000000-0000-0000-0000-000000000002
U3=00000000-0000-0000-0000-000000000003
U4=00000000-0000-0000-0000-000000000004
B1=00000000-0000-0000-0000-00000000000b
B2=00000000-0000-0000-0000-00000000000c
B3=00000000-0000-0000-0000-00000000000d
B4=00000000-0000-0000-0000-00000000000e
B5=00000000-0000-0000-0000-00000000000f
B6=00000000-0000-0000-0000-000000000016
C2=00000000-0000-0000-0000-00000000000c

PASS=0; FAIL=0; FAILED_CASES=()

check() { # check <desc> <expected> <actual>
  local desc="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    PASS=$((PASS+1)); echo "PASS  $desc"
  else
    FAIL=$((FAIL+1)); FAILED_CASES+=("$desc (期望 $expected 实际 $actual)")
    echo "FAIL  $desc (期望 $expected 实际 $actual)"
  fi
}

code() { curl -s -o /dev/null -w "%{http_code}" "$@"; }
body() { curl -s "$@"; }

echo "===== 1. 账号资格 ====="
check "1.1 正常账号 200" 200 "$(code $ACC/api/v1/accounts/me -H "Authorization: Bearer $T1")"
check "1.2 冻结账号 200" 200 "$(code $ACC/api/v1/accounts/me -H "Authorization: Bearer $T2")"
check "1.3 逾期账号 200" 200 "$(code $ACC/api/v1/accounts/me -H "Authorization: Bearer $T3")"
check "1.4 未认证 401" 401 "$(code $ACC/api/v1/accounts/me)"

echo "===== 2. 目录 ====="
check "2.1 分类列表 200" 200 "$(code $CAT/api/v1/categories)"
check "2.2 关键词检索 200" 200 "$(code "$CAT/api/v1/books?q=quantum")"
check "2.3 大小写不敏感 200" 200 "$(code "$CAT/api/v1/books?q=QUANTUM")"
check "2.4 百分号转义 200" 200 "$(code "$CAT/api/v1/books?q=%25")"
check "2.5 纯空白 200" 200 "$(code "$CAT/api/v1/books?q=%20%20")"
check "2.6 超长关键词 400" 400 "$(code "$CAT/api/v1/books?q=$(python3 -c 'print("a"*101)')")"
check "2.7 分类筛选 200" 200 "$(code "$CAT/api/v1/books?categoryId=$C2")"
check "2.8 type 筛选 200" 200 "$(code "$CAT/api/v1/books?type=PERIODICAL")"
check "2.9 availability 200" 200 "$(code "$CAT/api/v1/books?availability=true")"
check "2.10a title_asc 200" 200 "$(code "$CAT/api/v1/books?sort=title_asc")"
check "2.10b title_desc 200" 200 "$(code "$CAT/api/v1/books?sort=title_desc")"
check "2.11 pageSize>100 400" 400 "$(code "$CAT/api/v1/books?pageSize=101")"
check "2.12 页码越界 200" 200 "$(code "$CAT/api/v1/books?page=999")"
check "2.13 详情上架 200" 200 "$(code $CAT/api/v1/books/$B1)"
check "2.14 详情无库存 200" 200 "$(code $CAT/api/v1/books/$B2)"
check "2.15 详情下架 404" 404 "$(code $CAT/api/v1/books/$B3)"
check "2.16 详情移除 404" 404 "$(code $CAT/api/v1/books/$B4)"
check "2.17 详情不存在 404" 404 "$(code $CAT/api/v1/books/00000000-0000-0000-0000-0000000000ff)"
check "2.18 详情非法ID 400" 400 "$(code $CAT/api/v1/books/not-a-uuid)"

echo "===== 3. 借阅 ====="
# 3.1 正常借阅
R=$(body -X POST $LEN/api/v1/loans -H "Authorization: Bearer $T1" -H "Content-Type: application/json" -H "Idempotency-Key: verify-borrow-$(date +%s)" -d "{\"catalogItemId\":\"$B1\"}")
LOAN_ID=$(echo "$R" | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['id'])" 2>/dev/null)
check "3.1 正常借阅 201" 201 "$(echo "$R" | python3 -c "import json,sys; print(201 if 'data' in json.load(sys.stdin) else 500)" 2>/dev/null)"
echo "     loan_id=$LOAN_ID"

# 3.2 幂等重放
KEY="verify-replay-$(date +%s)"
C1=$(code -X POST $LEN/api/v1/loans -H "Authorization: Bearer $T1" -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" -d "{\"catalogItemId\":\"$B5\"}")
HDR=$(curl -s -D - -o /dev/null -X POST $LEN/api/v1/loans -H "Authorization: Bearer $T1" -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" -d "{\"catalogItemId\":\"$B5\"}" | grep -i "Idempotent-Replay" | tr -d '\r')
check "3.2 幂等重放 201+Replay" "201" "$C1"
echo "     重放头: $HDR"

# 3.3 同 key 异载荷
KEY="verify-conflict-$(date +%s)"
C1=$(code -X POST $LEN/api/v1/loans -H "Authorization: Bearer $T1" -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" -d "{\"catalogItemId\":\"$B6\"}")
C2=$(code -X POST $LEN/api/v1/loans -H "Authorization: Bearer $T1" -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" -d "{\"catalogItemId\":\"$B5\"}")
check "3.3a 同key异载荷 首次201" "201" "$C1"
check "3.3b 同key异载荷 二次409" "409" "$C2"

check "3.4 缺幂等key 400" 400 "$(code -X POST $LEN/api/v1/loans -H "Authorization: Bearer $T1" -H "Content-Type: application/json" -d "{\"catalogItemId\":\"$B1\"}")"
check "3.5 冻结账号 403" 403 "$(code -X POST $LEN/api/v1/loans -H "Authorization: Bearer $T2" -H "Content-Type: application/json" -H "Idempotency-Key: v-u2" -d "{\"catalogItemId\":\"$B1\"}")"
check "3.6 逾期账号 409" 409 "$(code -X POST $LEN/api/v1/loans -H "Authorization: Bearer $T3" -H "Content-Type: application/json" -H "Idempotency-Key: v-u3" -d "{\"catalogItemId\":\"$B1\"}")"
check "3.7 借满账号 409" 409 "$(code -X POST $LEN/api/v1/loans -H "Authorization: Bearer $T4" -H "Content-Type: application/json" -H "Idempotency-Key: v-u4" -d "{\"catalogItemId\":\"$B5\"}")"
check "3.8 无库存 409" 409 "$(code -X POST $LEN/api/v1/loans -H "Authorization: Bearer $T1" -H "Content-Type: application/json" -H "Idempotency-Key: v-b2" -d "{\"catalogItemId\":\"$B2\"}")"
check "3.9 下架 409" 409 "$(code -X POST $LEN/api/v1/loans -H "Authorization: Bearer $T1" -H "Content-Type: application/json" -H "Idempotency-Key: v-b3" -d "{\"catalogItemId\":\"$B3\"}")"
check "3.10 移除 404" 404 "$(code -X POST $LEN/api/v1/loans -H "Authorization: Bearer $T1" -H "Content-Type: application/json" -H "Idempotency-Key: v-b4" -d "{\"catalogItemId\":\"$B4\"}")"
check "3.12 非法ID 400" 400 "$(code -X POST $LEN/api/v1/loans -H "Authorization: Bearer $T1" -H "Content-Type: application/json" -H "Idempotency-Key: v-bad" -d '{"catalogItemId":"not-a-uuid"}')"
check "3.13 空请求体 400" 400 "$(code -X POST $LEN/api/v1/loans -H "Authorization: Bearer $T1" -H "Content-Type: application/json" -H "Idempotency-Key: v-empty" -d '{}')"

echo "===== 4. 归还 ====="
check "4.1 正常归还 200" 200 "$(code -X POST $LEN/api/v1/loans/$LOAN_ID/return -H "Authorization: Bearer $T1" -H "Content-Type: application/json" -H "Idempotency-Key: v-ret-$LOAN_ID")"
check "4.2 重复归还 409" 409 "$(code -X POST $LEN/api/v1/loans/$LOAN_ID/return -H "Authorization: Bearer $T1" -H "Content-Type: application/json" -H "Idempotency-Key: v-ret2-$LOAN_ID")"
check "4.3 归还他人 404" 404 "$(code -X POST $LEN/api/v1/loans/$LOAN_ID/return -H "Authorization: Bearer $T2" -H "Content-Type: application/json" -H "Idempotency-Key: v-ret-other")"
check "4.4 逾期归还 200" 200 "$(code -X POST $LEN/api/v1/loans/00000000-0000-0000-0000-000000000031/return -H "Authorization: Bearer $T3" -H "Content-Type: application/json" -H "Idempotency-Key: v-ret-u3")"
check "4.5 归还不存在 404" 404 "$(code -X POST $LEN/api/v1/loans/00000000-0000-0000-0000-0000000000ff/return -H "Authorization: Bearer $T1" -H "Content-Type: application/json" -H "Idempotency-Key: v-ret-none")"

echo "===== 5. 个人借阅记录 ====="
check "5.1 本人列表 200" 200 "$(code "$LEN/api/v1/users/$U1/loans" -H "Authorization: Bearer $T1")"
check "5.2 ACTIVE 200" 200 "$(code "$LEN/api/v1/users/$U1/loans?status=ACTIVE" -H "Authorization: Bearer $T1")"
check "5.3 OVERDUE 200" 200 "$(code "$LEN/api/v1/users/$U3/loans?status=OVERDUE" -H "Authorization: Bearer $T3")"
check "5.4 COMPLETED 200" 200 "$(code "$LEN/api/v1/users/$U1/loans?status=COMPLETED" -H "Authorization: Bearer $T1")"
check "5.5 非法状态 400" 400 "$(code "$LEN/api/v1/users/$U1/loans?status=BOGUS" -H "Authorization: Bearer $T1")"
check "5.6 非本人 404" 404 "$(code "$LEN/api/v1/users/$U2/loans" -H "Authorization: Bearer $T1")"
check "5.7 单条详情 200" 200 "$(code "$LEN/api/v1/loans/$LOAN_ID" -H "Authorization: Bearer $T1")"
check "5.8 详情非本人 404" 404 "$(code "$LEN/api/v1/loans/$LOAN_ID" -H "Authorization: Bearer $T2")"

echo "===== 6. 幂等操作查询 ====="
check "6.1 查询已记录 200" 200 "$(code "$LEN/api/v1/loan-operations/$KEY" -H "Authorization: Bearer $T1")"
check "6.2 查询未记录 404" 404 "$(code "$LEN/api/v1/loan-operations/nonexistent-key" -H "Authorization: Bearer $T1")"
check "6.3 查询他人 404" 404 "$(code "$LEN/api/v1/loan-operations/$KEY" -H "Authorization: Bearer $T2")"

echo ""
echo "===== 结果: PASS=$PASS FAIL=$FAIL ====="
if [ $FAIL -gt 0 ]; then
  printf '%s\n' "${FAILED_CASES[@]}"
  exit 1
fi
