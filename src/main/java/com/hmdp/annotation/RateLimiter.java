package com.hmdp.annotation;

import java.lang.annotation.*;

/**
 * 滑动窗口限流注解，支持全局 / IP / 用户维度。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimiter {

    /** 限流 key 前缀 */
    String key() default "rate:limit";

    /** 时间窗口（秒） */
    long windowSeconds() default 60;

    /** 窗口内最大请求数 */
    int count() default 10;

    /** 维度：global / ip / user */
    String dimension() default "global";
}
