package com.example;

import com.example.grpc.OrderReply;
import com.example.grpc.UserReply;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebControllerTest {

    private UserGrpcClient userClient;
    private OrderGrpcClient orderClient;
    private WebController controller;

    @BeforeEach
    void setUp() {
        userClient = mock(UserGrpcClient.class);
        orderClient = mock(OrderGrpcClient.class);
        controller = new WebController(userClient, orderClient);
        when(userClient.getUser(anyString()))
                .thenReturn(UserReply.newBuilder().setId("1").setName("tester").build());
    }

    @Test
    void queryZeroReturnsMessageOnly() {
        Map<String, String> result = controller.queryZero();
        assertEquals("web service queryZero called, query self only", result.get("message"));
    }

    @Test
    void userOneReturnsUserFromGrpc() {
        Map<String, String> result = controller.userOne();
        assertEquals("1", result.get("id"));
        assertEquals("tester", result.get("name"));
    }

    @Test
    void queryOrderCombinesUserAndOrder() {
        when(orderClient.getOrder(anyString()))
                .thenReturn(OrderReply.newBuilder().setOrderId("1").setOrderName("smile").setName("tester").build());

        Map<String, Object> result = controller.queryOrder();

        assertEquals("tester", result.get("name"));
        assertEquals("1", ((Map<?, ?>) result.get("order")).get("orderId"));
        assertEquals("smile", ((Map<?, ?>) result.get("order")).get("orderName"));
    }

    @Test
    void queryOrderErrorPropagatesGrpcError() {
        when(orderClient.getOrderError(anyString()))
                .thenThrow(Status.INTERNAL.withDescription("Order service error").asRuntimeException());

        assertThrows(RuntimeException.class, () -> controller.queryOrderError(false));
        verify(userClient).getUser(anyString());
    }

    @Test
    void queryOrderErrorWithResponseTrueUsesHealthyPath() {
        when(orderClient.getOrder(anyString()))
                .thenReturn(OrderReply.newBuilder().setOrderId("1").setOrderName("smile").setName("tester").build());

        Map<String, Object> result = controller.queryOrderError(true);

        assertEquals("tester", result.get("name"));
    }
}
