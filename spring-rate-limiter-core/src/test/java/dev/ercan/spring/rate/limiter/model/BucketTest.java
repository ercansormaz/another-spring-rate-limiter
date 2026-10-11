package dev.ercan.spring.rate.limiter.model;

import dev.ercan.spring.rate.limiter.consumer.TokenBucketConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BucketTest {

  @Mock
  private TokenBucketConsumer consumer;

  private Bandwidth b1;
  private Bandwidth b2;

  @BeforeEach
  void setUp() {
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
  @DisplayName("Should throw exception when builder key is null or blank")
  void shouldThrowExceptionWhenKeyIsNullOrEmpty() {
    assertThatThrownBy(() -> Bucket.builder(consumer).key(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("key cannot be null or blank");

    assertThatThrownBy(() -> Bucket.builder(consumer).key("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("key cannot be null or blank");
  }

  @Test
  @DisplayName("Should throw exception when bandwidth is null")
  void shouldThrowExceptionWhenBandwidthIsNull() {
    Bucket.BucketLimitBuilder builder = Bucket.builder(consumer).key("test-key");

    assertThatThrownBy(() -> builder.addLimit(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Bandwidth cannot be null");
  }

  @Test
  @DisplayName("Should throw exception when duplicate bandwidth IDs are added")
  void shouldThrowExceptionWhenDuplicateBandwidthIdsAreAdded() {
    Bandwidth b1Duplicate = Bandwidth.builder("bw-1")
        .capacity(20)
        .refillIntervally(2, Duration.ofSeconds(1))
        .build();

    Bucket.Builder builder = Bucket.builder(consumer)
        .key("user-1")
        .addLimit(b1)
        .addLimit(b1Duplicate);

    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Bandwidth identifiers must be unique.");
  }

  @Test
  @DisplayName("Single bandwidth tryConsume should invoke consumer method")
  void singleBandwidthTryConsume() {
    Bucket bucket = Bucket.builder(consumer)
        .key("user-1")
        .addLimit(b1)
        .build();

    when(consumer.tryConsume("user-1", 1, List.of(b1))).thenReturn(true);
    when(consumer.tryConsume("user-1", 5, List.of(b1))).thenReturn(false);

    assertThat(bucket.tryConsume()).isTrue();
    assertThat(bucket.tryConsume(5)).isFalse();

    verify(consumer).tryConsume("user-1", 1, List.of(b1));
    verify(consumer).tryConsume("user-1", 5, List.of(b1));
  }

  @Test
  @DisplayName("Multi bandwidth tryConsume should invoke consumer multi method")
  void multiBandwidthTryConsume() {
    Bucket bucket = Bucket.builder(consumer)
        .key("user-1")
        .addLimit(b1)
        .addLimit(b2)
        .build();

    List<Bandwidth> bandwidths = List.of(b1, b2);
    when(consumer.tryConsume("user-1", 2, bandwidths)).thenReturn(true);

    assertThat(bucket.tryConsume(2)).isTrue();
    verify(consumer).tryConsume("user-1", 2, bandwidths);
  }

  @Test
  @DisplayName("Single bandwidth tryConsumeAndGet should invoke consumer method")
  void singleBandwidthTryConsumeAndGet() {
    Bucket bucket = Bucket.builder(consumer)
        .key("user-1")
        .addLimit(b1)
        .build();

    ConsumptionResult expectedResult = new ConsumptionResult(true, 9, Instant.now());
    when(consumer.tryConsumeAndGet("user-1", 1, List.of(b1))).thenReturn(expectedResult);

    ConsumptionResult result = bucket.tryConsumeAndGet();
    assertThat(result).isEqualTo(expectedResult);
    verify(consumer).tryConsumeAndGet("user-1", 1, List.of(b1));
  }

  @Test
  @DisplayName("Multi bandwidth tryConsumeAndGet should invoke consumer multi method")
  void multiBandwidthTryConsumeAndGet() {
    Bucket bucket = Bucket.builder(consumer)
        .key("user-1")
        .addLimit(b1)
        .addLimit(b2)
        .build();

    List<Bandwidth> bandwidths = List.of(b1, b2);
    ConsumptionResult expectedResult = new ConsumptionResult(true, 8, Instant.now());
    when(consumer.tryConsumeAndGet("user-1", 2, bandwidths)).thenReturn(expectedResult);

    ConsumptionResult result = bucket.tryConsumeAndGet(2);
    assertThat(result).isEqualTo(expectedResult);
    verify(consumer).tryConsumeAndGet("user-1", 2, bandwidths);
  }

  @Test
  @DisplayName("Single bandwidth estimateAbilityToConsume should invoke consumer method")
  void singleBandwidthEstimateAbilityToConsume() {
    Bucket bucket = Bucket.builder(consumer)
        .key("user-1")
        .addLimit(b1)
        .build();

    EstimationResult expectedResult = new EstimationResult(true, 10, Instant.now());
    when(consumer.estimateAbilityToConsume("user-1", 3, List.of(b1))).thenReturn(expectedResult);

    EstimationResult result = bucket.estimateAbilityToConsume(3);
    assertThat(result).isEqualTo(expectedResult);
    verify(consumer).estimateAbilityToConsume("user-1", 3, List.of(b1));
  }

  @Test
  @DisplayName("Multi bandwidth estimateAbilityToConsume should invoke consumer multi method")
  void multiBandwidthEstimateAbilityToConsume() {
    Bucket bucket = Bucket.builder(consumer)
        .key("user-1")
        .addLimit(b1)
        .addLimit(b2)
        .build();

    List<Bandwidth> bandwidths = List.of(b1, b2);
    EstimationResult expectedResult = new EstimationResult(false, 0, Instant.now());
    when(consumer.estimateAbilityToConsume("user-1", 15, bandwidths)).thenReturn(expectedResult);

    EstimationResult result = bucket.estimateAbilityToConsume(15);
    assertThat(result).isEqualTo(expectedResult);
    verify(consumer).estimateAbilityToConsume("user-1", 15, bandwidths);
  }

  @Test
  @DisplayName("Single bandwidth addTokens should invoke consumer method")
  void singleBandwidthAddTokens() {
    Bucket bucket = Bucket.builder(consumer)
        .key("user-1")
        .addLimit(b1)
        .build();

    bucket.addTokens(5);
    verify(consumer).addTokens("user-1", 5, List.of(b1));
  }

  @Test
  @DisplayName("Multi bandwidth addTokens should invoke consumer multi method")
  void multiBandwidthAddTokens() {
    Bucket bucket = Bucket.builder(consumer)
        .key("user-1")
        .addLimit(b1)
        .addLimit(b2)
        .build();

    List<Bandwidth> bandwidths = List.of(b1, b2);
    bucket.addTokens(10);
    verify(consumer).addTokens("user-1", 10, bandwidths);
  }

  @Test
  @DisplayName("Single and multi bandwidth reset should invoke consumer reset method")
  void bucketReset() {
    Bucket singleBucket = Bucket.builder(consumer).key("user-1").addLimit(b1).build();
    singleBucket.reset();
    verify(consumer).reset("user-1", List.of(b1));

    Bucket multiBucket = Bucket.builder(consumer).key("user-2").addLimit(b1).addLimit(b2).build();
    multiBucket.reset();
    verify(consumer).reset("user-2", List.of(b1, b2));
  }

  @Test
  @DisplayName("Should throw exception when tokens to consume/add is less than 1")
  void shouldThrowExceptionWhenTokensIsInvalid() {
    Bucket bucket = Bucket.builder(consumer).key("user-1").addLimit(b1).build();

    assertThatThrownBy(() -> bucket.tryConsume(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("tokens to consume must be greater than 0");

    assertThatThrownBy(() -> bucket.tryConsumeAndGet(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("tokens to consume must be greater than 0");

    assertThatThrownBy(() -> bucket.estimateAbilityToConsume(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("tokens to consume must be greater than 0");

    assertThatThrownBy(() -> bucket.addTokens(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("tokensToAdd must be greater than 0");
  }

  @Test
  @DisplayName("Should return key and bandwidths correctly")
  void shouldReturnKeyAndBandwidths() {
    Bucket bucket = Bucket.builder(consumer).key("user-1").addLimit(b1).addLimit(b2).build();

    assertThat(bucket.getKey()).isEqualTo("user-1");
    assertThat(bucket.getBandwidths()).containsExactly(b1, b2);
  }
}
