package com.notification.service;

import com.notification.channel.DeliveryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(redisTemplate);
        ReflectionTestUtils.setField(service, "idempotencyTtlSeconds", 86400L);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void getCachedResult_miss_returnsEmpty() {
        when(valueOps.get("idempotency:key1")).thenReturn(null);

        Optional<DeliveryResult> result = service.getCachedResult("key1");

        assertThat(result).isEmpty();
    }

    @Test
    void getCachedResult_hit_returnsCachedResult() {
        DeliveryResult cached = DeliveryResult.builder().success(true).messageId("m1").build();
        when(valueOps.get("idempotency:key1")).thenReturn(cached);

        Optional<DeliveryResult> result = service.getCachedResult("key1");

        assertThat(result).contains(cached);
    }

    @Test
    void cacheResult_setsValueWithTtl() {
        DeliveryResult result = DeliveryResult.builder().success(true).messageId("msg").build();

        service.cacheResult("my-key", result);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), durationCaptor.capture());
        assertThat(keyCaptor.getValue()).isEqualTo("idempotency:my-key");
        assertThat(valueCaptor.getValue()).isEqualTo(result);
        assertThat(durationCaptor.getValue().getSeconds()).isEqualTo(86400);
    }
}
