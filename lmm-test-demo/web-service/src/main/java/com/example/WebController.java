package com.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.Map;

@RestController
public class WebController {
    private static final Logger logger = LoggerFactory.getLogger(WebController.class);

    @Value("${USER_SERVICE_URL:http://user-service:8081}")
    private String userServiceUrl;

    @Value("${ORDER_SERVICE_URL:http://order-service:8082}")
    private String orderServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

     @GetMapping("/api/webquery/queryZero")
    public Map<String, String> queryZero() {
        logger.info("web service queryZero called, query self only");

        // 调用 user 服务
        Map<String, String> userResult = new HashMap<>();
        userResult.put("message", "web service queryZero called, query self only");

        return userResult;
    }

    /**
     * 调用user服务，只返回用户信息
     */
    @GetMapping("/api/webquery/userOne")
    public Map<String, String> userOne() {
        logger.info("web service userOne called, calling user service");

        // 调用 user 服务
        Map<String, String> userResult = restTemplate.getForObject(
            userServiceUrl + "/api/userquery/user",
            Map.class
        );

        return userResult;
    }

    /**
     * 先调用user服务获取name，再调用order服务获取订单信息，最后组合返回结果
     * @return
     */
    @GetMapping("/api/webquery/orderTwo")
    public Map<String, Object> queryOrder() {
        logger.info("web service called, calling user and order services");

        // 调用 user 服务
        Map<String, String> userResult = restTemplate.getForObject(
            userServiceUrl + "/api/userquery/user",
            Map.class
        );

        // 调用 order 服务
        Map<String, String> orderResult = restTemplate.getForObject(
            orderServiceUrl + "/api/orderquery/order",
            Map.class
        );

        // 组合返回结果
        Map<String, Object> response = new HashMap<>();
        response.put("user", userResult);
        response.put("order", orderResult);
        response.put("name", userResult.get("name"));
        return response;
    }

    @GetMapping("/api/webquery/orderTwoError")
    public Map<String, Object> queryOrderError(@RequestParam(required = false, defaultValue = "false") Boolean response) {
        logger.info("web service error endpoint called, response={}, calling user service", response);

        // 调用 user 服务
        Map<String, String> userResult = restTemplate.getForObject(
            userServiceUrl + "/api/userquery/user",
            Map.class
        );

        // 根据 response 参数决定调用哪个 order 接口
        String orderUrl = response
            ? orderServiceUrl + "/api/orderquery/order"
            : orderServiceUrl + "/api/orderquery/order/error";

        // 调用 order 服务
        Map<String, String> orderResult = restTemplate.getForObject(
            orderUrl,
            Map.class
        );

        // 组合返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("user", userResult);
        result.put("order", orderResult);
        result.put("name", userResult.get("name"));
        return result;
    }
}
