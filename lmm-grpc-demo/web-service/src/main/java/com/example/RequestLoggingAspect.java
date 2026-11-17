package com.example;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

@Aspect
@Component
public class RequestLoggingAspect {
    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingAspect.class);

    private static ThreadLocal<Long> startTime = new ThreadLocal<>();

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void controllerMethods() {}

    @Before("controllerMethods()")
    public void logRequest(JoinPoint joinPoint) {
        long start = System.currentTimeMillis();
        startTime.set(start);

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String apiPath = "";
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            apiPath = request.getRequestURI();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getMethod().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        logger.info("[{}] >>> 方法入口 - 类: {}, 方法: {}, API路径: {}", start, className, methodName, apiPath);
    }

    @AfterReturning(pointcut = "controllerMethods()", returning = "result")
    public void logResponse(JoinPoint joinPoint, Object result) {
        long start = startTime.get();
        long duration = System.currentTimeMillis() - start;
        startTime.remove();

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String apiPath = "";
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            apiPath = request.getRequestURI();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getMethod().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        logger.info("[{}] <<< 方法出口 - 类: {}, 方法: {}, API路径: {}, 耗时: {}ms", start, className, methodName, apiPath, duration);
    }
}
