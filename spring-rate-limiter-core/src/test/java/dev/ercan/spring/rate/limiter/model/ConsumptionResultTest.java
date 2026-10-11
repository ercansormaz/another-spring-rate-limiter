package dev.ercan.spring.rate.limiter.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ConsumptionResultTest {

  @Test
  @DisplayName("ConsumptionResult record properties should match constructor arguments")
  void recordPropertiesShouldMatch() {
    Instant now = Instant.now();
    ConsumptionResult result = new ConsumptionResult(true, 5L, now);

    assertThat(result.consumed()).isTrue();
    assertThat(result.remainingTokens()).isEqualTo(5L);
    assertThat(result.refillAt()).isEqualTo(now);
  }
}
