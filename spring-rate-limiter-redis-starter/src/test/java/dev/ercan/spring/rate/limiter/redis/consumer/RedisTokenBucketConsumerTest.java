package dev.ercan.spring.rate.limiter.redis.consumer;

import dev.ercan.spring.rate.limiter.model.Bandwidth;
import dev.ercan.spring.rate.limiter.model.ConsumptionResult;
import dev.ercan.spring.rate.limiter.model.EstimationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisTokenBucketConsumerTest {

  @Mock
  private RedisTemplate<String, String> redisTemplate;

  @Captor
  private ArgumentCaptor<List<String>> keysCaptor;

  @Captor
  private ArgumentCaptor<Object[]> argsCaptor;

  private RedisTokenBucketConsumer consumer;

  private Bandwidth b1;
  private Bandwidth b2;

  @BeforeEach
  void setUp() {
    consumer = new RedisTokenBucketConsumer(redisTemplate);

    b1 = Bandwidth.builder("bw-1")
        .capacity(10)
        .refillIntervally(1, Duration.ofSeconds(1))
        .build();

    b2 = Bandwidth.builder("bw-2")
        .capacity(100)
        .refillIntervally(10, Duration.ofMinutes(1))
        .build();
  }

  @Test
  @DisplayName("Should throw NullPointerException when redisTemplate is null")
  void shouldThrowExceptionWhenRedisTemplateIsNull() {
    assertThatThrownBy(() -> new RedisTokenBucketConsumer(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("redisTemplate cannot be null");
  }

  // --- tryConsume Single ---

  @Test
  @DisplayName("tryConsume single should return false if tokens requested exceeds capacity without calling redis")
  void tryConsumeSingleExceedsCapacity() {
    boolean result = consumer.tryConsume("user-1", 15, List.of(b1));

    assertThat(result).isFalse();
    verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
  }

  @Test
  @DisplayName("tryConsume single should execute script and return boolean")
  @SuppressWarnings("unchecked")
  void tryConsumeSingleSuccess() {
    doReturn(1L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

    boolean result = consumer.tryConsume("user-1", 5, List.of(b1));

    assertThat(result).isTrue();
    verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), argsCaptor.capture());

    assertThat(keysCaptor.getValue()).containsExactly("rl:tb:{user-1}:bw-1");
    Object[] args = argsCaptor.getValue();
    assertThat(args).hasSize(7);
    assertThat(args[1]).isEqualTo("5"); // tokens
    assertThat(args[2]).isEqualTo("10"); // initial
    assertThat(args[3]).isEqualTo("10"); // capacity
    assertThat(args[4]).isEqualTo("1"); // refill rate
    assertThat(args[5]).isEqualTo("1000"); // refill period millis
  }

  @Test
  @DisplayName("tryConsume single should throw exception if redis returns null")
  @SuppressWarnings("unchecked")
  void tryConsumeSingleNullResult() {
    doReturn(null).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

    assertThatThrownBy(() -> consumer.tryConsume("user-1", 1, List.of(b1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Invalid result from redis");
  }

  // --- tryConsume Multi ---

  @Test
  @DisplayName("tryConsume multi should return false if tokens requested exceeds any capacity")
  void tryConsumeMultiExceedsCapacity() {
    boolean result = consumer.tryConsume("user-1", 15, List.of(b1, b2));

    assertThat(result).isFalse();
    verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
  }

  @Test
  @DisplayName("tryConsume multi should execute script for multiple bandwidths")
  @SuppressWarnings("unchecked")
  void tryConsumeMultiSuccess() {
    doReturn(1L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

    boolean result = consumer.tryConsume("user-1", 5, List.of(b1, b2));

    assertThat(result).isTrue();
    verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), argsCaptor.capture());

    assertThat(keysCaptor.getValue()).containsExactly("rl:tb:{user-1}:bw-1", "rl:tb:{user-1}:bw-2");
    Object[] args = argsCaptor.getValue();
    // 2 (now, tokens) + 5 * 2 (bandwidth args) = 12
    assertThat(args).hasSize(12);
    assertThat(args[1]).isEqualTo("5"); // requested tokens
  }

  // --- tryConsumeAndGet Single & Multi ---

  @Test
  @DisplayName("tryConsumeAndGet single should return IMPOSSIBLE_CONSUMPTION if requested tokens exceed capacity")
  void tryConsumeAndGetSingleExceedsCapacity() {
    ConsumptionResult result = consumer.tryConsumeAndGet("user-1", 20, List.of(b1));

    assertThat(result.consumed()).isFalse();
    assertThat(result.remainingTokens()).isZero();
    assertThat(result.refillAt()).isNull();
    verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
  }

  @Test
  @DisplayName("tryConsumeAndGet single should parse redis result correctly")
  @SuppressWarnings("unchecked")
  void tryConsumeAndGetSingleSuccess() {
    long refillTime = Instant.now().toEpochMilli() + 1000;
    List<Long> mockRedisResult = List.of(1L, 7L, refillTime);
    doReturn(mockRedisResult).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

    ConsumptionResult result = consumer.tryConsumeAndGet("user-1", 3, List.of(b1));

    assertThat(result.consumed()).isTrue();
    assertThat(result.remainingTokens()).isEqualTo(7L);
    assertThat(result.refillAt()).isEqualTo(Instant.ofEpochMilli(refillTime));
  }

  @Test
  @DisplayName("tryConsumeAndGet should throw exception if redis returns invalid list")
  @SuppressWarnings("unchecked")
  void tryConsumeAndGetInvalidList() {
    doReturn(List.of(1L)).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

    assertThatThrownBy(() -> consumer.tryConsumeAndGet("user-1", 1, List.of(b1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Invalid result from redis");
  }

  @Test
  @DisplayName("tryConsumeAndGet multi should return IMPOSSIBLE_CONSUMPTION if requested tokens exceed capacity")
  void tryConsumeAndGetMultiExceedsCapacity() {
    ConsumptionResult result = consumer.tryConsumeAndGet("user-1", 15, List.of(b1, b2));

    assertThat(result.consumed()).isFalse();
    verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
  }

  @Test
  @DisplayName("tryConsumeAndGet multi should parse redis result correctly")
  @SuppressWarnings("unchecked")
  void tryConsumeAndGetMultiSuccess() {
    long refillTime = Instant.now().toEpochMilli() + 5000;
    List<Long> mockRedisResult = List.of(0L, 2L, refillTime);
    doReturn(mockRedisResult).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

    ConsumptionResult result = consumer.tryConsumeAndGet("user-1", 5, List.of(b1, b2));

    assertThat(result.consumed()).isFalse();
    assertThat(result.remainingTokens()).isEqualTo(2L);
    assertThat(result.refillAt()).isEqualTo(Instant.ofEpochMilli(refillTime));
  }

  // --- estimateAbilityToConsume Single & Multi ---

  @Test
  @DisplayName("estimateAbilityToConsume single should return IMPOSSIBLE_ESTIMATION if requested tokens exceed capacity")
  void estimateAbilityToConsumeSingleExceedsCapacity() {
    EstimationResult result = consumer.estimateAbilityToConsume("user-1", 50, List.of(b1));

    assertThat(result.canBeConsumed()).isFalse();
    assertThat(result.remainingTokens()).isZero();
    assertThat(result.refillAt()).isNull();
    verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
  }

  @Test
  @DisplayName("estimateAbilityToConsume single should parse redis result correctly")
  @SuppressWarnings("unchecked")
  void estimateAbilityToConsumeSingleSuccess() {
    long refillTime = Instant.now().toEpochMilli() + 2000;
    List<Long> mockRedisResult = List.of(1L, 10L, refillTime);
    doReturn(mockRedisResult).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

    EstimationResult result = consumer.estimateAbilityToConsume("user-1", 2, List.of(b1));

    assertThat(result.canBeConsumed()).isTrue();
    assertThat(result.remainingTokens()).isEqualTo(10L);
    assertThat(result.refillAt()).isEqualTo(Instant.ofEpochMilli(refillTime));
  }

  @Test
  @DisplayName("estimateAbilityToConsume multi should return IMPOSSIBLE_ESTIMATION if requested tokens exceed capacity")
  void estimateAbilityToConsumeMultiExceedsCapacity() {
    EstimationResult result = consumer.estimateAbilityToConsume("user-1", 20, List.of(b1, b2));

    assertThat(result.canBeConsumed()).isFalse();
    verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
  }

  @Test
  @DisplayName("estimateAbilityToConsume multi should parse redis result correctly")
  @SuppressWarnings("unchecked")
  void estimateAbilityToConsumeMultiSuccess() {
    long refillTime = Instant.now().toEpochMilli() + 3000;
    List<Long> mockRedisResult = List.of(1L, 8L, refillTime);
    doReturn(mockRedisResult).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

    EstimationResult result = consumer.estimateAbilityToConsume("user-1", 1, List.of(b1, b2));

    assertThat(result.canBeConsumed()).isTrue();
    assertThat(result.remainingTokens()).isEqualTo(8L);
    assertThat(result.refillAt()).isEqualTo(Instant.ofEpochMilli(refillTime));
  }

  // --- addTokens Single & Multi ---

  @Test
  @DisplayName("addTokens single should execute add token script")
  @SuppressWarnings("unchecked")
  void addTokensSingleSuccess() {
    doReturn(1L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

    consumer.addTokens("user-1", 5, List.of(b1));

    verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), argsCaptor.capture());
    assertThat(keysCaptor.getValue()).containsExactly("rl:tb:{user-1}:bw-1");
  }

  @Test
  @DisplayName("addTokens single should throw exception if redis returns null")
  @SuppressWarnings("unchecked")
  void addTokensSingleNullResult() {
    doReturn(null).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

    assertThatThrownBy(() -> consumer.addTokens("user-1", 5, List.of(b1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Invalid result from redis");
  }

  @Test
  @DisplayName("addTokens multi should execute multi add token script")
  @SuppressWarnings("unchecked")
  void addTokensMultiSuccess() {
    doReturn(1L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

    consumer.addTokens("user-1", 10, List.of(b1, b2));

    verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), argsCaptor.capture());
    assertThat(keysCaptor.getValue()).containsExactly("rl:tb:{user-1}:bw-1", "rl:tb:{user-1}:bw-2");
  }

  // --- reset Single & Multi ---

  @Test
  @DisplayName("reset single should delete key from redis")
  void resetSingle() {
    consumer.reset("user-1", List.of(b1));

    verify(redisTemplate).delete(List.of("rl:tb:{user-1}:bw-1"));
  }

  @Test
  @DisplayName("reset multi should delete multiple keys from redis")
  void resetMulti() {
    consumer.reset("user-1", List.of(b1, b2));

    verify(redisTemplate).delete(List.of("rl:tb:{user-1}:bw-1", "rl:tb:{user-1}:bw-2"));
  }
}
