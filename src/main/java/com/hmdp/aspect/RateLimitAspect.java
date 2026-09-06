package com.hmdp.aspect;

import com.hmdp.annotation.RateLimiter;
import com.hmdp.dto.Result;
import com.hmdp.utils.UserHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

/**
 * Redis + AOP 滑动窗口限流切面。
 */
@Aspect
@Component
public class RateLimitAspect {

    private static final DefaultRedisScript<Long> SLIDING_WINDOW_SCRIPT;

    static {
        SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>();
        SLIDING_WINDOW_SCRIPT.setLocation(new ClassPathResource("sliding_window.lua"));
        SLIDING_WINDOW_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Around("@annotation(com.hmdp.annotation.RateLimiter)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimiter rateLimiter = method.getAnnotation(RateLimiter.class);

        String key = buildKey(rateLimiter);
        Long allowed = stringRedisTemplate.execute(
                SLIDING_WINDOW_SCRIPT,
                java.util.Collections.singletonList(key),
                String.valueOf(rateLimiter.windowSeconds()),
                String.valueOf(rateLimiter.count()),
                String.valueOf(System.currentTimeMillis())
        );

        if (allowed == null || allowed == 0L) {
            return Result.fail("操作过于频繁，请稍后再试");
        }
        return joinPoint.proceed();
    }

    private String buildKey(RateLimiter rateLimiter) {
        String base = rateLimiter.key();
        String dimension = rateLimiter.dimension();
        if ("ip".equalsIgnoreCase(dimension)) {
            return base + ":ip:" + clientIp();
        }
        if ("user".equalsIgnoreCase(dimension)) {
            Long userId = UserHolder.getUser() == null ? 0L : UserHolder.getUser().getId();
            return base + ":user:" + userId;
        }
        return base;
    }

    private String clientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
