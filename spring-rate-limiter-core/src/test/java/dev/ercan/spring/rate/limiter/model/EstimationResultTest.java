package dev.ercan.spring.rate.limiter.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EstimationResultTest {

  @Test
  @DisplayName("EstimationResult record properties should match constructor arguments")
  void recordPropertiesShouldMatch() {
    Instant now = Instant.now();
    EstimationResult result = new EstimationResult(false, 0L, now);

    assertThat(result.canBeConsumed()).isFalse();
    assertThat(result.remainingTokens()).isZero();
    assertThat(result.refillAt()).isEqualTo(now);
  }
}
