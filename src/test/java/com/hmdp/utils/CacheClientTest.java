package com.hmdp.utils;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CacheClientTest {
    @SuppressWarnings("unchecked")
    private void checkRebuild(boolean fails) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        Shop old = new Shop();
        old.setId(1L);
        RedisData data = new RedisData();
        data.setData(old);
        data.setExpireTime(LocalDateTime.now().minusSeconds(1));
        when(values.get("cache:shop:1")).thenReturn(JSONUtil.toJsonStr(data));
        when(values.setIfAbsent(eq("lock:shop:1"), anyString(), eq(10L), eq(TimeUnit.SECONDS))).thenReturn(true);
        Function<Long, Shop> fallback = id -> {
            if (fails) throw new IllegalStateException("synthetic database failure");
            return old;
        };
        Shop result = new CacheClient(redis).queryWithLogicalExpire("cache:shop:", 1L, Shop.class, fallback, 1L, TimeUnit.MINUTES);
        assertEquals(Long.valueOf(1), result.getId());
        verify(redis, timeout(2000)).execute(any(RedisScript.class), eq(Collections.singletonList("lock:shop:1")), anyString());
        verify(redis, never()).delete(anyString());
        if (!fails) verify(values).set(eq("cache:shop:1"), anyString());
        else verify(values, never()).set(eq("cache:shop:1"), anyString());
        org.mockito.ArgumentCaptor<String> token = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(values).setIfAbsent(eq("lock:shop:1"), token.capture(), eq(10L), eq(TimeUnit.SECONDS));
        assertNotEquals("1", token.getValue());
        verify(redis).execute(any(RedisScript.class), eq(Collections.singletonList("lock:shop:1")), eq(token.getValue()));
    }

    @Test void successfulRebuildReleasesOnlyItsOwnLock() { checkRebuild(false); }
    @Test void failedRebuildReleasesOnlyItsOwnLock() { checkRebuild(true); }

    @Test @SuppressWarnings("unchecked")
    void losingLockDoesNotRebuildOrUnlock() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        RedisData data = new RedisData();
        Shop shop = new Shop(); shop.setId(1L); data.setData(shop);
        data.setExpireTime(LocalDateTime.now().minusSeconds(1));
        when(values.get(anyString())).thenReturn(JSONUtil.toJsonStr(data));
        when(values.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(false);
        new CacheClient(redis).queryWithLogicalExpire("cache:shop:", 1L, Shop.class,
            id -> { fail("Lock loser must not query database"); return null; }, 1L, TimeUnit.MINUTES);
        verify(redis, never()).delete(anyString());
        verify(redis, never()).execute(any(RedisScript.class), anyList(), anyString());
    }
}
