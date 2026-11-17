package com.example;

import com.example.grpc.GetUserRequest;
import com.example.grpc.UserReply;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserGrpcServiceTest {

    @Test
    void getUserReturnsFixedUser() {
        UserGrpcService service = new UserGrpcService();
        CapturingObserver observer = new CapturingObserver();

        service.getUser(GetUserRequest.newBuilder().setId("1").build(), observer);

        assertNull(observer.error);
        assertTrue(observer.completed, "onCompleted 应被调用");
        assertEquals("1", observer.reply.getId());
        assertEquals("tester", observer.reply.getName());
    }

    private static class CapturingObserver implements StreamObserver<UserReply> {
        private UserReply reply;
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(UserReply value) {
            this.reply = value;
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }
}
