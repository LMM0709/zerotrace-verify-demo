#!/bin/bash
# 端到端验证：从 web 服务（NodePort）依次调用四个接口，校验返回内容。
set -u

NAMESPACE="lmm-grpc-demo"
FAIL=0

NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')
NODE_PORT=$(kubectl get svc web-service -n "$NAMESPACE" -o jsonpath='{.spec.ports[0].nodePort}')
BASE="http://$NODE_IP:$NODE_PORT"

check() {
    local name="$1" url="$2" expect_code="$3" expect_body="$4"
    local code body
    body=$(curl -s -o /tmp/e2e-body.txt -w '%{http_code}' "$url")
    code="$body"
    body=$(cat /tmp/e2e-body.txt)

    if [ "$code" != "$expect_code" ]; then
        echo "FAIL [$name] 期望 HTTP $expect_code，实际 $code: $body"
        FAIL=1
        return
    fi
    if [ -n "$expect_body" ] && ! printf '%s' "$body" | grep -q "$expect_body"; then
        echo "FAIL [$name] 返回内容不含 '$expect_body': $body"
        FAIL=1
        return
    fi
    echo "PASS [$name] HTTP $code $body"
}

echo "== 1. queryZero（仅 web 自身）"
check queryZero "$BASE/api/webquery/queryZero" 200 "query self only"

echo "== 2. userOne（web -gRPC-> user）"
check userOne "$BASE/api/webquery/userOne" 200 '"name":"tester"'

echo "== 3. orderTwo（web -gRPC-> user + order -gRPC-> user）"
check orderTwo "$BASE/api/webquery/orderTwo" 200 '"orderName":"smile"'

echo "== 4. orderTwoError（order 返回 gRPC 错误，web 应 500）"
check orderTwoError "$BASE/api/webquery/orderTwoError" 500 "Order service error"

echo "== 5. orderTwoError?response=true（走正常分支）"
check orderTwoErrorHealthy "$BASE/api/webquery/orderTwoError?response=true" 200 '"orderName":"smile"'

if [ "$FAIL" -ne 0 ]; then
    echo ""
    echo "端到端验证失败"
    exit 1
fi
echo ""
echo "端到端验证全部通过"
