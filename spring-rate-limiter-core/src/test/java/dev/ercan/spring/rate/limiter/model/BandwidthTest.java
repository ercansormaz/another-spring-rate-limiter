package dev.ercan.spring.rate.limiter.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BandwidthTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t", "\n"})
  @DisplayName("Should throw exception when bandwidth id is null or blank")
  void shouldThrowExceptionWhenIdIsNullOrEmpty(String id) {
    assertThatThrownBy(() -> Bandwidth.builder(id))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("id cannot be null or blank");
  }

  @Test
  @DisplayName("Should throw exception when capacity is less than 1")
  void shouldThrowExceptionWhenCapacityIsInvalid() {
    Bandwidth.BandwidthCapacityBuilder builder = Bandwidth.builder("test-id");

    assertThatThrownBy(() -> builder.capacity(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("capacity must be positive");

    assertThatThrownBy(() -> builder.capacity(-5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("capacity must be positive");
  }

  @Test
  @DisplayName("Should throw exception for invalid refillGreedy parameters")
  void shouldThrowExceptionForInvalidRefillGreedy() {
    Bandwidth.RefillBuilder refillBuilder = Bandwidth.builder("test-id").capacity(10);

    assertThatThrownBy(() -> refillBuilder.refillGreedy(0, Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Refill tokens must be positive");

    assertThatThrownBy(() -> refillBuilder.refillGreedy(5, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Refill period can not be null");

    assertThatThrownBy(() -> refillBuilder.refillGreedy(5, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Refill period should be positive");
  }

  @Test
  @DisplayName("Should correctly calculate greedy refill period")
  void shouldCalculateGreedyRefillPeriod() {
    Bandwidth bandwidth = Bandwidth.builder("test-id")
        .capacity(100)
        .refillGreedy(10, Duration.ofSeconds(1))
        .build();

    assertThat(bandwidth.getId()).isEqualTo("test-id");
    assertThat(bandwidth.getCapacity()).isEqualTo(100);
    assertThat(bandwidth.getInitial()).isEqualTo(100);
    assertThat(bandwidth.getRefillRate()).isEqualTo(10);
    assertThat(bandwidth.getRefillPeriod()).isEqualTo(Duration.ofMillis(100));
  }

  @Test
  @DisplayName("Should throw exception for invalid refillIntervally parameters")
  void shouldThrowExceptionForInvalidRefillIntervally() {
    Bandwidth.RefillBuilder refillBuilder = Bandwidth.builder("test-id").capacity(10);

    assertThatThrownBy(() -> refillBuilder.refillIntervally(5, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Refill period can not be null");

    assertThatThrownBy(() -> refillBuilder.refillIntervally(0, Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Refill tokens must be positive");

    assertThatThrownBy(() -> refillBuilder.refillIntervally(5, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Refill period should be positive");
  }

  @Test
  @DisplayName("Should correctly create intervally refill bandwidth")
  void shouldCreateIntervallyRefillBandwidth() {
    Bandwidth bandwidth = Bandwidth.builder("test-id")
        .capacity(50)
        .refillIntervally(5, Duration.ofSeconds(10))
        .build();

    assertThat(bandwidth.getId()).isEqualTo("test-id");
    assertThat(bandwidth.getCapacity()).isEqualTo(50);
    assertThat(bandwidth.getInitial()).isEqualTo(50);
    assertThat(bandwidth.getRefillRate()).isEqualTo(5);
    assertThat(bandwidth.getRefillPeriod()).isEqualTo(Duration.ofSeconds(10));
  }

  @Test
  @DisplayName("Should set custom initial tokens count")
  void shouldSetCustomInitialTokens() {
    Bandwidth bandwidth = Bandwidth.builder("test-id")
        .capacity(100)
        .refillIntervally(10, Duration.ofSeconds(1))
        .initialTokens(20)
        .build();

    assertThat(bandwidth.getInitial()).isEqualTo(20);
    assertThat(bandwidth.getCapacity()).isEqualTo(100);
  }

  @Test
  @DisplayName("Should throw exception when initial tokens count is negative")
  void shouldThrowExceptionWhenInitialTokensIsNegative() {
    Bandwidth.Builder builder = Bandwidth.builder("test-id")
        .capacity(100)
        .refillIntervally(10, Duration.ofSeconds(1));

    assertThatThrownBy(() -> builder.initialTokens(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Initial tokens count must be positive");
  }
}
