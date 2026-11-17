package com.example;

import com.example.grpc.GetUserRequest;
import com.example.grpc.UserReply;
import com.example.grpc.UserServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 * user 服务的 gRPC 客户端（替换原 RestTemplate 调用）。
 */
@Component
public class UserGrpcClient {

    private static final long DEADLINE_SECONDS = 3L;

    private final ManagedChannel channel;
    private final UserServiceGrpc.UserServiceBlockingStub stub;

    public UserGrpcClient(@Value("${user.service.grpc-address:localhost:9091}") String address) {
        this.channel = ManagedChannelBuilder.forTarget(address)
                .usePlaintext()
                .build();
        this.stub = UserServiceGrpc.newBlockingStub(channel);
    }

    public UserReply getUser(String id) {
        return stub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                .getUser(GetUserRequest.newBuilder().setId(id).build());
    }

    @PreDestroy
    public void shutdown() {
        channel.shutdown();
    }
}
