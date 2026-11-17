#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

NAMESPACE="lmm-grpc-demo"
IMAGE_LOADER_YAML="$SCRIPT_DIR/image-loader.yaml"
SERVICES="user-service order-service web-service"
IMAGE_PREFIX="grpc"

echo "=========================================="
echo "0. 同步 proto 定义"
echo "=========================================="
sh "$SCRIPT_DIR/proto/sync-protos.sh"

echo ""
echo "=========================================="
echo "1. 编译并运行单元测试"
echo "=========================================="
for service in $SERVICES; do
    echo "--- $service: mvn test"
    (cd "$SCRIPT_DIR/$service" && mvn clean test -q)
done
echo "单元测试全部通过"

echo ""
echo "=========================================="
echo "2. 打包 Java 项目"
echo "=========================================="
for service in $SERVICES; do
    (cd "$SCRIPT_DIR/$service" && mvn package -DskipTests -q)
done
echo "Java 项目打包完成"

echo ""
echo "=========================================="
echo "3. 修复 docker-entrypoint.sh (Alpine 使用 /bin/sh)"
echo "=========================================="
for service in $SERVICES; do
    printf '#!/bin/sh\nexec java -jar /home/%s.jar\n' "$service" > "$SCRIPT_DIR/$service/docker-entrypoint.sh"
    echo "  修复 $service/docker-entrypoint.sh"
done

echo ""
echo "=========================================="
echo "4. 构建 Docker 镜像 (使用 --network=host --no-cache)"
echo "=========================================="
for service in $SERVICES; do
    (cd "$SCRIPT_DIR/$service" && docker build --network=host --no-cache -t "$service-$IMAGE_PREFIX:1.0" . && echo "  $service-$IMAGE_PREFIX:1.0 构建完成")
done

echo ""
echo "=========================================="
echo "5. 验证镜像内容"
echo "=========================================="
for service in $SERVICES; do
    count=$(docker run --rm --entrypoint /bin/sh "$service-$IMAGE_PREFIX:1.0" -c "ls -la /home/*.jar" 2>/dev/null | grep -c "$service.jar" || echo "0")
    if [ "$count" = "1" ]; then
        echo "  $service 镜像验证通过"
    else
        echo "  ERROR: $service 镜像打包错误!"
        exit 1
    fi
done

echo ""
echo "=========================================="
echo "6. 导出镜像为 tar 包"
echo "=========================================="
cd /tmp
for service in $SERVICES; do
    docker save "$service-$IMAGE_PREFIX:1.0" -o "$service-$IMAGE_PREFIX.tar"
done
echo "  镜像导出完成"

echo ""
echo "=========================================="
echo "7. 创建 image-loader Pod 并导入镜像到 containerd"
echo "=========================================="
kubectl get ns "$NAMESPACE" >/dev/null 2>&1 || kubectl create namespace "$NAMESPACE"
kubectl apply -f "$IMAGE_LOADER_YAML"
echo "  等待 image-loader Pod 就绪..."
kubectl wait --for=condition=ready pod/image-loader -n "$NAMESPACE" --timeout=120s

echo "  导入镜像到 containerd..."
for service in $SERVICES; do
    kubectl exec image-loader -n "$NAMESPACE" -- sh -c "/host-usr-bin/ctr -n k8s.io images import /tar/$service-$IMAGE_PREFIX.tar"
done

echo "  删除 image-loader..."
kubectl delete -f "$IMAGE_LOADER_YAML" --force

echo ""
echo "=========================================="
echo "8. 部署到 Kubernetes"
echo "=========================================="
kubectl apply -f "$SCRIPT_DIR/k8s.yaml"
echo "  K8s 资源部署完成"

echo ""
echo "=========================================="
echo "9. 等待 Pods 就绪"
echo "=========================================="
kubectl rollout restart deployment -n "$NAMESPACE" >/dev/null 2>&1 || true
for service in $SERVICES; do
    kubectl rollout status "deployment/$service" -n "$NAMESPACE" --timeout=180s || echo "  $service 等待超时"
done

echo ""
echo "=========================================="
echo "10. 部署完成 - 检查状态"
echo "=========================================="
kubectl get pods -n "$NAMESPACE"
echo ""
kubectl get svc -n "$NAMESPACE"
echo ""

NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')
NODE_PORT=$(kubectl get svc web-service -n "$NAMESPACE" -o jsonpath='{.spec.ports[0].nodePort}')

echo "=========================================="
echo "访问接口方式:"
echo "=========================================="
echo ""
echo "【User 服务】ClusterIP，仅 gRPC 9091，集群内访问"
echo "【Order 服务】ClusterIP，仅 gRPC 9092，集群内访问"
echo "【Web 服务】NodePort，HTTP 9090，可从外部访问"
echo "  外部访问: curl http://$NODE_IP:$NODE_PORT/api/webquery/orderTwo"
echo ""
echo "  预期返回: {\"user\":{\"id\":\"1\",\"name\":\"tester\"},\"order\":{\"orderId\":\"1\",\"orderName\":\"smile\",\"name\":\"tester\"},\"name\":\"tester\"}"
echo ""
echo "执行端到端验证: $SCRIPT_DIR/test-e2e.sh"
