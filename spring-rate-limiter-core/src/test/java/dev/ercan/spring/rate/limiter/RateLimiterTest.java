package dev.ercan.spring.rate.limiter;

import dev.ercan.spring.rate.limiter.consumer.TokenBucketConsumer;
import dev.ercan.spring.rate.limiter.model.Bucket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RateLimiterTest {

  @Mock
  private TokenBucketConsumer tokenBucketConsumer;

  @Test
  @DisplayName("RateLimiter builder should return BucketKeyBuilder")
  void builderShouldReturnBucketKeyBuilder() {
    RateLimiter rateLimiter = new RateLimiter(tokenBucketConsumer);

    Bucket.BucketKeyBuilder builder = rateLimiter.builder();
    assertThat(builder).isNotNull();
  }
}
