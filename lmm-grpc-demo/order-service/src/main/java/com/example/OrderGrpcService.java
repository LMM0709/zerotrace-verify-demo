package com.example;

import com.example.grpc.GetOrderRequest;
import com.example.grpc.OrderReply;
import com.example.grpc.OrderServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 订单 gRPC 服务实现。
 * - getOrder 等价于原 GET /api/orderquery/order：先通过 gRPC 查用户，再返回订单。
 * - getOrderError 等价于原 GET /api/orderquery/order/error：查用户后返回错误。
 */
@GrpcService
public class OrderGrpcService extends OrderServiceGrpc.OrderServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(OrderGrpcService.class);

    public static final String DEFAULT_ORDER_ID = "1";
    public static final String DEFAULT_ORDER_NAME = "smile";
    public static final String ERROR_MESSAGE = "Order service error";

    private final UserGrpcClient userGrpcClient;

    public OrderGrpcService(UserGrpcClient userGrpcClient) {
        this.userGrpcClient = userGrpcClient;
    }

    @Override
    public void getOrder(GetOrderRequest request, StreamObserver<OrderReply> responseObserver) {
        logger.info("order service gRPC GetOrder called, calling user service via gRPC");

        String name = userGrpcClient.getUser("1").getName();

        OrderReply reply = OrderReply.newBuilder()
                .setOrderId(DEFAULT_ORDER_ID)
                .setOrderName(DEFAULT_ORDER_NAME)
                .setName(name)
                .build();

        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    @Override
    public void getOrderError(GetOrderRequest request, StreamObserver<OrderReply> responseObserver) {
        // 与原实现一致：先调用 user 服务，再抛错
        String name = userGrpcClient.getUser("1").getName();
        logger.info("order service gRPC GetOrderError called, user name={}, throwing error", name);

        responseObserver.onError(Status.INTERNAL
                .withDescription(ERROR_MESSAGE)
                .asRuntimeException());
    }
}
