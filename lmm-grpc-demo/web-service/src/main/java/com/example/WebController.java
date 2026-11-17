package com.example;

import com.example.grpc.OrderReply;
import com.example.grpc.UserReply;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对外 REST 接口与原 lmm-test-demo 完全一致（同样的路径、同样的返回结构），
 * 区别是 web -> user / web -> order 的调用改为 gRPC。
 */
@RestController
public class WebController {
    private static final Logger logger = LoggerFactory.getLogger(WebController.class);

    private final UserGrpcClient userGrpcClient;
    private final OrderGrpcClient orderGrpcClient;

    public WebController(UserGrpcClient userGrpcClient, OrderGrpcClient orderGrpcClient) {
        this.userGrpcClient = userGrpcClient;
        this.orderGrpcClient = orderGrpcClient;
    }

    @GetMapping("/api/webquery/queryZero")
    public Map<String, String> queryZero() {
        logger.info("web service queryZero called, query self only");

        Map<String, String> userResult = new HashMap<>();
        userResult.put("message", "web service queryZero called, query self only");

        return userResult;
    }

    /**
     * 通过 gRPC 调用 user 服务，只返回用户信息
     */
    @GetMapping("/api/webquery/userOne")
    public Map<String, String> userOne() {
        logger.info("web service userOne called, calling user service");

        return toMap(userGrpcClient.getUser("1"));
    }

    /**
     * 先通过 gRPC 调用 user 服务获取 name，再通过 gRPC 调用 order 服务获取订单信息，最后组合返回结果
     */
    @GetMapping("/api/webquery/orderTwo")
    public Map<String, Object> queryOrder() {
        logger.info("web service called, calling user and order services");

        UserReply userResult = userGrpcClient.getUser("1");
        OrderReply orderResult = orderGrpcClient.getOrder("1");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("user", toMap(userResult));
        response.put("order", toMap(orderResult));
        response.put("name", userResult.getName());
        return response;
    }

    @GetMapping("/api/webquery/orderTwoError")
    public Map<String, Object> queryOrderError(@RequestParam(required = false, defaultValue = "false") Boolean response) {
        logger.info("web service error endpoint called, response={}, calling user service", response);

        UserReply userResult = userGrpcClient.getUser("1");

        OrderReply orderResult = response
                ? orderGrpcClient.getOrder("1")
                : orderGrpcClient.getOrderError("1");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", toMap(userResult));
        result.put("order", toMap(orderResult));
        result.put("name", userResult.getName());
        return result;
    }

    private static Map<String, String> toMap(UserReply reply) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("id", reply.getId());
        map.put("name", reply.getName());
        return map;
    }

    private static Map<String, String> toMap(OrderReply reply) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("orderId", reply.getOrderId());
        map.put("orderName", reply.getOrderName());
        map.put("name", reply.getName());
        return map;
    }
}
