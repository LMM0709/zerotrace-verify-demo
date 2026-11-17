package com.example;

import com.example.grpc.GetUserRequest;
import com.example.grpc.UserReply;
import com.example.grpc.UserServiceGrpc;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 用户 gRPC 服务实现：与原 user-service 的 GET /api/userquery/user 行为一致，
 * 固定返回 id=1 / name=tester。
 */
@GrpcService
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(UserGrpcService.class);

    public static final String DEFAULT_USER_ID = "1";
    public static final String DEFAULT_USER_NAME = "tester";

    @Override
    public void getUser(GetUserRequest request, StreamObserver<UserReply> responseObserver) {
        logger.info("user service gRPC GetUser called, id={}", request.getId());

        UserReply reply = UserReply.newBuilder()
                .setId(DEFAULT_USER_ID)
                .setName(DEFAULT_USER_NAME)
                .build();

        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
}
