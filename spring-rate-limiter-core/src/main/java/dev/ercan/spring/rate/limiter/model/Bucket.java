package dev.ercan.spring.rate.limiter.model;

import dev.ercan.spring.rate.limiter.consumer.TokenBucketConsumer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A named token bucket configured with one or more bandwidth limits. */
public class Bucket {

  private final String key;
  private final List<Bandwidth> bandwidths;
  private final TokenBucketConsumer tokenBucketConsumer;

  private Bucket(Builder builder) {
    this.key = builder.key;
    this.bandwidths = List.copyOf(builder.bandwidths);
    this.tokenBucketConsumer = builder.tokenBucketConsumer;
  }

  /**
 * Attempts to consume one token.
 *
 * @return {@code true} if the token was successfully consumed, {@code false} otherwise.
 */
  public boolean tryConsume() {
    return tryConsume(1);
  }

  /**
   * Attempts to consume the requested number of tokens.
   *
   * @param tokens number of tokens to consume
   * @return whether the consumption succeeded
   */
  public boolean tryConsume(long tokens) {
    validateTokens(tokens);
    return tokenBucketConsumer.tryConsume(key, tokens, bandwidths);
  }

  /**
 * Attempts to consume one token and returns the bucket state.
 *
 * @return {@link ConsumptionResult} containing the result of the consumption.
 */
  public ConsumptionResult tryConsumeAndGet() {
    return tryConsumeAndGet(1);
  }

  /**
   * Attempts to consume tokens and returns the resulting bucket state.
   *
   * @param tokens number of tokens to consume
   * @return consumption result
   */
  public ConsumptionResult tryConsumeAndGet(long tokens) {
    validateTokens(tokens);
    return tokenBucketConsumer.tryConsumeAndGet(key, tokens, bandwidths);
  }

  /**
   * Estimates whether the requested tokens can be consumed.
   *
   * @param tokens number of tokens to estimate
   * @return estimation result; bucket state is not changed
   */
  public EstimationResult estimateAbilityToConsume(long tokens) {
    validateTokens(tokens);
    return tokenBucketConsumer.estimateAbilityToConsume(key, tokens, bandwidths);
  }

  /**
   * Adds tokens to this bucket, subject to its configured capacity.
   *
   * @param tokensToAdd number of tokens to add
   */
  public void addTokens(long tokensToAdd) {
    if (tokensToAdd < 1) {
      throw new IllegalArgumentException("tokensToAdd must be greater than 0");
    }

    tokenBucketConsumer.addTokens(key, tokensToAdd, bandwidths);
  }

  /** Removes this bucket's persisted state. */
  public void reset() {
    tokenBucketConsumer.reset(key, bandwidths);
  }

  /**
 * Returns the identifier key for this bucket.
 *
 * @return key identifying this bucket
 */
  public String getKey() {
    return key;
  }

  /**
 * Returns the list of bandwidth limits configured for this bucket.
 *
 * @return configured bandwidth limits
 */
  public List<Bandwidth> getBandwidths() {
    return bandwidths;
  }

  private static void validateTokens(long tokens) {
    if (tokens < 1) {
      throw new IllegalArgumentException("tokens to consume must be greater than 0");
    }
  }

  /**
 * Starts building a bucket using the supplied consumer.
 *
 * @param tokenBucketConsumer the consumer that will be used for bucket operations
 * @return a {@link BucketKeyBuilder} to configure the bucket's key and limits
 */
  public static BucketKeyBuilder builder(TokenBucketConsumer tokenBucketConsumer) {
    return new BucketKeyBuilder(tokenBucketConsumer);
  }

  /** Builder step for assigning a bucket key. */
  public static class BucketKeyBuilder {

    private final TokenBucketConsumer tokenBucketConsumer;

    /**
 * Creates a builder step for the supplied consumer.
 *
 * @param tokenBucketConsumer the consumer that will be used for bucket operations
 */
    public BucketKeyBuilder(TokenBucketConsumer tokenBucketConsumer) {
      this.tokenBucketConsumer = Objects.requireNonNull(tokenBucketConsumer, "tokenBucketConsumer cannot be null");
    }

    /**
     * Sets the bucket key.
     *
     * @param key bucket identifier
     * @return builder step for adding limits
     */
    public BucketLimitBuilder key(String key) {
      if (key == null || key.isBlank()) {
        throw new IllegalArgumentException("key cannot be null or blank");
      }
      return new BucketLimitBuilder(tokenBucketConsumer, key);
    }

  }

  /** Builder step for adding the first bandwidth limit. */
  public static class BucketLimitBuilder {

    private final String key;
    private final TokenBucketConsumer tokenBucketConsumer;

    /**
 * Creates a builder with the first bandwidth limit.
 *
 * @param tokenBucketConsumer the consumer that will be used for bucket operations
 * @param key                 the identifier for the bucket
 */
    public BucketLimitBuilder(TokenBucketConsumer tokenBucketConsumer, String key) {
      this.key = key;
      this.tokenBucketConsumer = tokenBucketConsumer;
    }

    /**
     * Adds the first bandwidth limit.
     *
     * @param bandwidth bandwidth definition
     * @return builder that accepts additional limits or builds the bucket
     */
    public Builder addLimit(Bandwidth bandwidth) {
      Objects.requireNonNull(bandwidth, "Bandwidth cannot be null");
      return new Builder(tokenBucketConsumer, key, bandwidth);
    }

  }

  /** Builder for a fully configured bucket. */
  public static class Builder {

    private final String key;
    private final TokenBucketConsumer tokenBucketConsumer;
    private final List<Bandwidth> bandwidths = new ArrayList<>();

    /**
 * Creates a builder with its first bandwidth limit.
 *
 * @param tokenBucketConsumer the consumer that will be used for bucket operations
 * @param key                 the identifier for the bucket
 * @param bandwidth           the initial bandwidth limit for the bucket
 */
    public Builder(TokenBucketConsumer tokenBucketConsumer, String key, Bandwidth bandwidth) {
      this.tokenBucketConsumer = tokenBucketConsumer;
      this.key = key;
      this.bandwidths.add(bandwidth);
    }

    /**
     * Adds another bandwidth limit.
     *
     * @param bandwidth bandwidth definition
     * @return this builder
     */
    public Builder addLimit(Bandwidth bandwidth) {
      Objects.requireNonNull(bandwidth, "Bandwidth cannot be null");
      bandwidths.add(bandwidth);
      return this;
    }

    /**
     * Builds the bucket.
     *
     * @return configured bucket
     * @throws IllegalArgumentException if bandwidth identifiers are duplicated
     */
    public Bucket build() {
      if (bandwidths.size() > 1) {
        Set<String> ids = new HashSet<>();
        for (Bandwidth b : bandwidths) {
          if (!ids.add(b.getId())) {
            throw new IllegalArgumentException("Bandwidth identifiers must be unique.");
          }
        }
      }

      return new Bucket(this);
    }
  }
}
