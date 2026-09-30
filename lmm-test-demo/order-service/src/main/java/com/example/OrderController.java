package com.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class OrderController {
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    @Value("${USER_SERVICE_URL:http://user-service:8081}")
    private String userServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/api/orderquery/order")
    public Map<String, Object> queryOrder() {
        logger.info("order service called, calling user service");

        // 调用 user 服务获取 name
        Map<String, String> userResult = restTemplate.getForObject(
            userServiceUrl + "/api/userquery/user",
            Map.class
        );

        String name = userResult != null ? userResult.get("name") : null;

        // 构建返回结果
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orderId", "1");
        response.put("orderName", "smile");
        response.put("name", name);

        return response;
    }

    @GetMapping("/api/orderquery/order/error")
    public Map<String, Object> queryOrderError() {
         // 调用 user 服务获取 name
        Map<String, String> userResult = restTemplate.getForObject(
            userServiceUrl + "/api/userquery/user",
            Map.class
        );
        logger.info("order service error endpoint called, throwing exception");
        throw new RuntimeException("Order service error");
    }
}
