#!/bin/sh
# proto/ 是唯一事实来源，本脚本把 proto 同步到各服务模块的 src/main/proto。
# 模块内保留副本，是为了让每个服务都能单独 mvn/docker 构建。
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

for service in user-service order-service web-service; do
    mkdir -p "$ROOT_DIR/$service/src/main/proto"
    rm -f "$ROOT_DIR/$service/src/main/proto"/*.proto
done

cp "$SCRIPT_DIR/user.proto" "$ROOT_DIR/user-service/src/main/proto/"
cp "$SCRIPT_DIR/user.proto" "$ROOT_DIR/order-service/src/main/proto/"
cp "$SCRIPT_DIR/order.proto" "$ROOT_DIR/order-service/src/main/proto/"
cp "$SCRIPT_DIR/user.proto" "$ROOT_DIR/web-service/src/main/proto/"
cp "$SCRIPT_DIR/order.proto" "$ROOT_DIR/web-service/src/main/proto/"

echo "proto 已同步到各服务模块"
