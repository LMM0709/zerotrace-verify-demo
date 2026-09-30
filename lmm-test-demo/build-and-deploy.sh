#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

NAMESPACE="lmm-test-demo"
IMAGE_LOADER_YAML="$SCRIPT_DIR/image-loader.yaml"

echo "=========================================="
echo "1. 编译 Java 项目"
echo "=========================================="
cd "$SCRIPT_DIR/user-service" && mvn clean package -DskipTests -q
cd "$SCRIPT_DIR/order-service" && mvn clean package -DskipTests -q
cd "$SCRIPT_DIR/web-service" && mvn clean package -DskipTests -q
echo "Java 项目编译完成"

echo ""
echo "=========================================="
echo "2. 修复 docker-entrypoint.sh (Alpine 使用 /bin/sh)"
echo "=========================================="
for service in user-service order-service web-service; do
    echo "#!/bin/sh" > "$SCRIPT_DIR/$service/docker-entrypoint.sh"
    echo "exec java -jar /home/$service.jar" >> "$SCRIPT_DIR/$service/docker-entrypoint.sh"
    echo "  修复 $service/docker-entrypoint.sh"
done

echo ""
echo "=========================================="
echo "3. 构建 Docker 镜像 (使用 --network=host --no-cache)"
echo "=========================================="
cd "$SCRIPT_DIR/user-service" && docker build --network=host --no-cache -t user-service:1.0 . && echo "  user-service:1.0 构建完成"
cd "$SCRIPT_DIR/order-service" && docker build --network=host --no-cache -t order-service:1.0 . && echo "  order-service:1.0 构建完成"
cd "$SCRIPT_DIR/web-service" && docker build --network=host --no-cache -t web-service:1.0 . && echo "  web-service:1.0 构建完成"

echo ""
echo "=========================================="
echo "4. 验证镜像内容"
echo "=========================================="
for service in user-service order-service web-service; do
    count=$(docker run --rm --entrypoint /bin/sh $service:1.0 -c "ls -la /home/*.jar" 2>/dev/null | grep -c "$service.jar" || echo "0")
    if [ "$count" = "1" ]; then
        echo "  $service 镜像验证通过"
    else
        echo "  ERROR: $service 镜像打包错误!"
        exit 1
    fi
done

echo ""
echo "=========================================="
echo "5. 导出镜像为 tar 包"
echo "=========================================="
cd /tmp
docker save user-service:1.0 -o user-service.tar
docker save order-service:1.0 -o order-service.tar
docker save web-service:1.0 -o web-service.tar
echo "  镜像导出完成"

echo ""
echo "=========================================="
echo "6. 创建 image-loader Pod 并导入镜像到 containerd"
echo "=========================================="
cat > "$IMAGE_LOADER_YAML" << 'EOF'
apiVersion: v1
kind: Pod
metadata:
  name: image-loader
  namespace: lmm-test-demo
spec:
  hostPID: true
  containers:
  - name: loader
    image: registry.cn-beijing.aliyuncs.com/deepflow-demo/opentelemetry-javaagent:1.15.0
    imagePullPolicy: IfNotPresent
    command: ["sh", "-c", "sleep 3600"]
    securityContext:
      privileged: true
    volumeMounts:
    - name: tar-files
      mountPath: /tar
    - name: containerd-sock
      mountPath: /run/containerd
    - name: usr-bin
      mountPath: /host-usr-bin
    - name: lib64
      mountPath: /lib64
  volumes:
  - name: tar-files
    hostPath:
      path: /tmp
  - name: containerd-sock
    hostPath:
      path: /run/containerd
  - name: usr-bin
    hostPath:
      path: /usr/bin
  - name: lib64
    hostPath:
      path: /lib64
EOF

kubectl apply -f "$IMAGE_LOADER_YAML"
echo "  等待 image-loader Pod 就绪..."
kubectl wait --for=condition=ready pod/image-loader -n "$NAMESPACE" --timeout=60s

echo "  导入镜像到 containerd..."
kubectl exec image-loader -n "$NAMESPACE" -- sh -c "/host-usr-bin/ctr -n k8s.io images import /tar/user-service.tar"
kubectl exec image-loader -n "$NAMESPACE" -- sh -c "/host-usr-bin/ctr -n k8s.io images import /tar/order-service.tar"
kubectl exec image-loader -n "$NAMESPACE" -- sh -c "/host-usr-bin/ctr -n k8s.io images import /tar/web-service.tar"

echo "  删除 image-loader..."
kubectl delete -f "$IMAGE_LOADER_YAML" --force

echo ""
echo "=========================================="
echo "7. 清理旧 Pods"
echo "=========================================="
kubectl delete pods --field-selector=status.phase!=Running -n "$NAMESPACE" 2>/dev/null || true
kubectl delete pod -l app=user-service -n "$NAMESPACE" --field-selector=status.phase=Running 2>/dev/null || true
kubectl delete pod -l app=order-service -n "$NAMESPACE" --field-selector=status.phase=Running 2>/dev/null || true
kubectl delete pod -l app=web-service -n "$NAMESPACE" --field-selector=status.phase=Running 2>/dev/null || true
echo "  旧 Pods 清理完成"

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
kubectl wait --for=condition=ready pod -l app=user-service -n "$NAMESPACE" --timeout=120s || echo "  user-service 等待超时"
kubectl wait --for=condition=ready pod -l app=order-service -n "$NAMESPACE" --timeout=120s || echo "  order-service 等待超时"
kubectl wait --for=condition=ready pod -l app=web-service -n "$NAMESPACE" --timeout=120s || echo "  web-service 等待超时"

echo ""
echo "=========================================="
echo "10. 部署完成 - 检查状态"
echo "=========================================="
echo ""
kubectl get pods -n "$NAMESPACE"
echo ""
kubectl get svc -n "$NAMESPACE"
echo ""

# 获取访问信息
NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')
NODE_PORT=$(kubectl get svc web-service -n "$NAMESPACE" -o jsonpath='{.spec.ports[0].nodePort}')

echo "=========================================="
echo "访问接口方式:"
echo "=========================================="
echo ""
echo "【User 服务】ClusterIP，无法直接从外部访问"
echo "  集群内访问: curl http://user-service:8081/api/query/user"
echo ""
echo "【Order 服务】ClusterIP，无法直接从外部访问"
echo "  集群内访问: curl http://order-service:8082/api/query/order"
echo ""
echo "【Web 服务】NodePort，可从外部访问"
echo "  外部访问: curl http://$NODE_IP:$NODE_PORT/api/query/order"
echo ""
echo "  预期返回: {\"user\":{\"id\":\"1\",\"name\":\"tester\"},\"order\":{\"orderId\":\"1\",\"orderName\":\"smile\"}}"
echo ""
