package dev.ercan.spring.rate.limiter;

import dev.ercan.spring.rate.limiter.consumer.TokenBucketConsumer;
import dev.ercan.spring.rate.limiter.model.Bucket;

/** Entry point for creating named token buckets. */
public class RateLimiter {

  private final TokenBucketConsumer tokenBucketConsumer;

  /**
   * Creates a rate limiter backed by the supplied consumer.
   *
   * @param tokenBucketConsumer storage and operation provider for bucket state
   */
  public RateLimiter(TokenBucketConsumer tokenBucketConsumer) {
    this.tokenBucketConsumer = tokenBucketConsumer;
  }

  /**
   * Starts building a bucket definition.
   *
   * @return builder used to set the bucket key and bandwidth limits
   */
  public Bucket.BucketKeyBuilder builder() {
    return new Bucket.BucketKeyBuilder(tokenBucketConsumer);
  }
}
