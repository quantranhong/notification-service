package com.notification.service;

import com.notification.channel.DeliveryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${notification.redis.idempotency-ttl-seconds:86400}")
    private long idempotencyTtlSeconds;

    private static final String KEY_PREFIX = "idempotency:";

    @SuppressWarnings("unchecked")
    public Optional<DeliveryResult> getCachedResult(String idempotencyKey) {
        Object raw = redisTemplate.opsForValue().get(KEY_PREFIX + idempotencyKey);
        if (raw instanceof DeliveryResult) return Optional.of((DeliveryResult) raw);
        return Optional.empty();
    }

    public void cacheResult(String idempotencyKey, DeliveryResult result) {
        redisTemplate.opsForValue().set(KEY_PREFIX + idempotencyKey, result, Duration.ofSeconds(idempotencyTtlSeconds));
    }
}
