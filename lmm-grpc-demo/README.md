# lmm-grpc-demo

`lmm-test-demo` 的 gRPC 版本：对外 REST 接口、返回内容完全一致，**服务之间的 HTTP 调用全部替换为 gRPC**。

## 与 lmm-test-demo 的端口对照

| 服务 | lmm-test-demo | lmm-grpc-demo | 说明 |
|------|:---:|:---:|------|
| web-service | 8080 | **9090** | 对外 HTTP，NodePort 访问 |
| user-service | 8081 | **9091** | 仅 gRPC（无 HTTP） |
| order-service | 8082 | **9092** | 仅 gRPC（无 HTTP） |

两套服务端口完全不同、Namespace 独立（`lmm-test-demo` vs `lmm-grpc-demo`），**可同时运行，互不影响**。

## 服务间调用关系（gRPC）

```
web-service :9090
  ├── gRPC → user-service :9091   (UserService/GetUser)
  └── gRPC → order-service :9092  (OrderService/GetOrder, GetOrderError)

order-service :9092
  └── gRPC → user-service :9091   (UserService/GetUser)
```

## 项目结构

```
lmm-grpc-demo/
├── proto/
│   ├── user.proto                 ← UserService gRPC IDL（唯一事实来源）
│   ├── order.proto                ← OrderService gRPC IDL
│   └── sync-protos.sh             ← 将 proto 同步到各模块的 src/main/proto/
├── user-service/                  ← gRPC 9091，只暴露 gRPC 接口
│   ├── pom.xml
│   ├── Dockerfile
│   ├── docker-entrypoint.sh
│   └── src/main/
│       ├── proto/user.proto
│       ├── java/com/example/
│       │   ├── UserServiceApplication.java
│       │   └── UserGrpcService.java        ← gRPC 服务实现
│       └── resources/application.yml
├── order-service/                 ← gRPC 9092，通过 gRPC 调用 user-service
│   ├── pom.xml
│   ├── Dockerfile
│   ├── docker-entrypoint.sh
│   └── src/main/
│       ├── proto/user.proto
│       ├── proto/order.proto
│       ├── java/com/example/
│       │   ├── OrderServiceApplication.java
│       │   ├── UserGrpcClient.java          ← user 服务 gRPC 客户端
│       │   └── OrderGrpcService.java        ← gRPC 服务实现
│       └── resources/application.yml
├── web-service/                   ← HTTP 9090（NodePort），通过 gRPC 调用 user/order
│   ├── pom.xml
│   ├── Dockerfile
│   ├── docker-entrypoint.sh
│   └── src/main/
│       ├── proto/user.proto
│       ├── proto/order.proto
│       ├── java/com/example/
│       │   ├── WebServiceApplication.java
│       │   ├── UserGrpcClient.java          ← user 服务 gRPC 客户端
│       │   ├── OrderGrpcClient.java         ← order 服务 gRPC 客户端
│       │   └── WebController.java           ← 对外 REST 接口
│       └── resources/application.yml
├── k8s.yaml                       ← Namespace / Deployment / Service
├── image-loader.yaml              ← 辅助 Pod：通过 ctr 将镜像导入 containerd
├── build-and-deploy.sh            ← 编译 → 测试 → 打包 → 构建镜像 → 导入 → 部署
├── test-e2e.sh                    ← 端到端验证脚本
└── README.md
```

## 对外 REST 接口（与 lmm-test-demo 完全一致）

| 接口 | 说明 | 预期返回 |
|------|------|----------|
| `GET /api/webquery/queryZero` | 仅查询自身 | `{"message":"web service queryZero called, query self only"}` |
| `GET /api/webquery/userOne` | web → user（gRPC） | `{"id":"1","name":"tester"}` |
| `GET /api/webquery/orderTwo` | web → user + order（gRPC） | `{"user":{...},"order":{...},"name":"tester"}` |
| `GET /api/webquery/orderTwoError?response=false` | web → user + order 抛错 | HTTP 500 |
| `GET /api/webquery/orderTwoError?response=true` | web → user + order 正常 | 同 `/orderTwo` |

## 编译 & 测试

```sh
# 同步 proto 定义
sh proto/sync-protos.sh

# 各模块单独编译并运行单元测试
for s in user-service order-service web-service; do
  mvn -B -f "$s/pom.xml" clean test
done
```

单元测试覆盖：
- `UserGrpcServiceTest` — 验证固定返回 id=1/name=tester
- `OrderGrpcServiceTest` — 验证正常链路（调用 user）和错误链路（调用 user 后抛 gRPC INTERNAL 错误）
- `WebControllerTest` — 验证四个 REST 接口的组装逻辑和错误透传

## 构建 Docker 镜像

```sh
# 打包（跳过测试）
for s in user-service order-service web-service; do
  mvn -B -f "$s/pom.xml" package -DskipTests
done

# 构建镜像
for s in user-service order-service web-service; do
  docker build --network=host --no-cache -t "$s-grpc:1.0" "$s"
done

# 验证镜像内容
for s in user-service order-service web-service; do
  docker run --rm --entrypoint /bin/sh "$s-grpc:1.0" -c "ls -la /home/*.jar"
done
```

## 部署到 Kubernetes

**前提：** 镜像已导入节点 containerd（节点无法访问 Docker Hub 时使用）。

### 步骤 1：导入镜像（如需要）

```sh
cd /tmp
docker save user-service-grpc:1.0 -o user-service-grpc.tar
docker save order-service-grpc:1.0 -o order-service-grpc.tar
docker save web-service-grpc:1.0 -o web-service-grpc.tar

kubectl apply -f image-loader.yaml
kubectl wait --for=condition=ready pod/image-loader -n lmm-grpc-demo --timeout=120s

for s in user-service order-service web-service; do
  kubectl exec image-loader -n lmm-grpc-demo -- \
    sh -c "/host-usr-bin/ctr -n k8s.io images import /tar/$s-grpc.tar"
done

kubectl delete -f image-loader.yaml --force
```

### 步骤 2：部署

```sh
kubectl create namespace lmm-grpc-demo 2>/dev/null || true
kubectl apply -f k8s.yaml

# 重启 Pod（使新 Deployment 生效）
kubectl rollout restart deployment -n lmm-grpc-demo
```

### 步骤 3：等待就绪

```sh
kubectl wait --for=condition=ready pod -l app=user-service -n lmm-grpc-demo --timeout=120s
kubectl wait --for=condition=ready pod -l app=order-service -n lmm-grpc-demo --timeout=120s
kubectl wait --for=condition=ready pod -l app=web-service -n lmm-grpc-demo --timeout=120s

kubectl get pods -n lmm-grpc-demo
```

## 端到端验证

```sh
NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')
NODE_PORT=$(kubectl get svc web-service -n lmm-grpc-demo -o jsonpath='{.spec.ports[0].nodePort}')
BASE="http://$NODE_IP:$NODE_PORT"

echo "=== queryZero ==="
curl -s "$BASE/api/webquery/queryZero"

echo "=== userOne ==="
curl -s "$BASE/api/webquery/userOne"

echo "=== orderTwo ==="
curl -s "$BASE/api/webquery/orderTwo"

echo "=== orderTwoError (expect 500) ==="
curl -s -o /dev/null -w "HTTP %{http_code}\n" "$BASE/api/webquery/orderTwoError"
```

**预期全部通过：**

```
=== queryZero ===
{"message":"web service queryZero called, query self only"}

=== userOne ===
{"id":"1","name":"tester"}

=== orderTwo ===
{"user":{"id":"1","name":"tester"},"order":{"orderId":"1","orderName":"smile","name":"tester"},"name":"tester"}

=== orderTwoError (expect 500) ===
HTTP 500
```

## 一键部署脚本

```sh
./build-and-deploy.sh   # 编译 → 测试 → 打包 → 构建镜像 → 导入 → 部署
./test-e2e.sh           # 端到端验证
```

> 注意：`build-and-deploy.sh` 内部已包含 `proto/sync-protos.sh` 调用，无需单独运行。

## 依赖版本

| 依赖 | 版本 |
|------|------|
| Java | 1.8 |
| Spring Boot | 2.7.18 |
| grpc-spring-boot-starter | 2.15.0.RELEASE |
| grpc-java | 1.54.2 |
| protobuf | 3.21.12 |
| junit-jupiter | 5.3.1 |
