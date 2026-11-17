package com.example;

import com.example.grpc.GetOrderRequest;
import com.example.grpc.OrderReply;
import com.example.grpc.UserReply;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderGrpcServiceTest {

    @Test
    void getOrderReturnsOrderWithUserName() {
        UserGrpcClient userClient = mock(UserGrpcClient.class);
        when(userClient.getUser(anyString()))
                .thenReturn(UserReply.newBuilder().setId("1").setName("tester").build());

        CapturingObserver observer = new CapturingObserver();
        new OrderGrpcService(userClient).getOrder(GetOrderRequest.newBuilder().setOrderId("1").build(), observer);

        assertTrue(observer.completed);
        assertNotNull(observer.reply);
        assertEquals("1", observer.reply.getOrderId());
        assertEquals("smile", observer.reply.getOrderName());
        assertEquals("tester", observer.reply.getName());
    }

    @Test
    void getOrderErrorCallsUserServiceThenFails() {
        UserGrpcClient userClient = mock(UserGrpcClient.class);
        when(userClient.getUser(anyString()))
                .thenReturn(UserReply.newBuilder().setId("1").setName("tester").build());

        CapturingObserver observer = new CapturingObserver();
        new OrderGrpcService(userClient).getOrderError(GetOrderRequest.newBuilder().setOrderId("1").build(), observer);

        verify(userClient).getUser(anyString());
        assertNotNull(observer.error, "应返回 gRPC 错误");
        assertTrue(observer.error instanceof StatusRuntimeException);
        StatusRuntimeException error = (StatusRuntimeException) observer.error;
        assertEquals(Status.Code.INTERNAL, error.getStatus().getCode());
        assertEquals(OrderGrpcService.ERROR_MESSAGE, error.getStatus().getDescription());
    }

    private static class CapturingObserver implements StreamObserver<OrderReply> {
        private OrderReply reply;
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(OrderReply value) {
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
