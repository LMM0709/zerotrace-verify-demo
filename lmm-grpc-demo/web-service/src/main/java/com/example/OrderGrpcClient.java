package com.example;

import com.example.grpc.GetOrderRequest;
import com.example.grpc.OrderReply;
import com.example.grpc.OrderServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 * order 服务的 gRPC 客户端（替换原 RestTemplate 调用）。
 */
@Component
public class OrderGrpcClient {

    private static final long DEADLINE_SECONDS = 3L;

    private final ManagedChannel channel;
    private final OrderServiceGrpc.OrderServiceBlockingStub stub;

    public OrderGrpcClient(@Value("${order.service.grpc-address:localhost:9092}") String address) {
        this.channel = ManagedChannelBuilder.forTarget(address)
                .usePlaintext()
                .build();
        this.stub = OrderServiceGrpc.newBlockingStub(channel);
    }

    public OrderReply getOrder(String orderId) {
        return stub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                .getOrder(GetOrderRequest.newBuilder().setOrderId(orderId).build());
    }

    /**
     * 调用 order 服务的出错接口（等价于原 /api/orderquery/order/error），
     * gRPC 错误会以 StatusRuntimeException 抛出。
     */
    public OrderReply getOrderError(String orderId) {
        return stub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                .getOrderError(GetOrderRequest.newBuilder().setOrderId(orderId).build());
    }

    @PreDestroy
    public void shutdown() {
        channel.shutdown();
    }
}
