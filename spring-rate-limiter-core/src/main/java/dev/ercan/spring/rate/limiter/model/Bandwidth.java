package dev.ercan.spring.rate.limiter.model;

import java.time.Duration;

/**
 * Defines the capacity and refill policy of a token bucket.
 *
 * <p>Use {@link #builder(String)} to create an instance. A bandwidth can refill
 * greedily (continuously, represented by a token rate and token period) or
 * intervally (in batches at fixed intervals).</p>
 */
public class Bandwidth {

  private final String id;
  private final long initial;
  private final long capacity;
  private final long refillRate;
  private final Duration refillPeriod;

  private Bandwidth(Builder builder) {
    this.id = builder.id;
    this.initial = builder.initial;
    this.capacity = builder.capacity;
    this.refillRate = builder.refillRate;
    this.refillPeriod = builder.refillPeriod;
  }

  /**
 * Returns the identifier used to distinguish this bandwidth from other limits.
 *
 * @return identifier used to distinguish this bandwidth from other limits
 */
  public String getId() {
    return id;
  }

  /**
 * Returns the number of tokens initially available in a new bucket.
 *
 * @return number of tokens initially available in a new bucket
 */
  public long getInitial() {
    return initial;
  }

  /**
 * Returns the maximum number of tokens the bucket can hold.
 *
 * @return maximum number of tokens the bucket can hold
 */
  public long getCapacity() {
    return capacity;
  }

  /**
 * Returns the number of tokens added per refill period.
 *
 * @return number of tokens added per refill period
 */
  public long getRefillRate() {
    return refillRate;
  }

  /**
 * Returns the duration between refill operations.
 *
 * @return duration between refill operations
 */
  public Duration getRefillPeriod() {
    return refillPeriod;
  }

  /**
   * Starts defining a bandwidth.
   *
   * @param id unique identifier of the bandwidth within a bucket
   * @return builder used to configure capacity
   * @throws IllegalArgumentException if {@code id} is null or blank
   */
  public static BandwidthCapacityBuilder builder(String id) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("id cannot be null or blank");
    }

    return new BandwidthCapacityBuilder(id);
  }

  /** Builder step for configuring a bandwidth capacity. */
  public static class BandwidthCapacityBuilder {

    private final String id;

    private BandwidthCapacityBuilder(String id) {
      this.id = id;
    }

    /**
     * Sets the maximum number of tokens.
     *
     * @param tokens maximum token count
     * @return builder used to configure refilling
     */
    public RefillBuilder capacity(long tokens) {
      if (tokens < 1) {
        throw new IllegalArgumentException("capacity must be positive");
      }

      return new RefillBuilder(id, tokens);
    }

  }

  /** Builder step for configuring a bandwidth refill policy. */
  public static class RefillBuilder {

    private final String id;
    private final long capacity;

    private RefillBuilder(String id, long capacity) {
      this.id = id;
      this.capacity = capacity;
    }

    /**
     * Configures continuous/greedy refilling.
     *
     * @param tokens number of tokens added during the complete period
     * @param period duration over which the tokens are added
     * @return builder used to configure initial tokens
     */
    public Builder refillGreedy(long tokens, Duration period) {
      if (tokens < 1) {
        throw new IllegalArgumentException("Refill tokens must be positive");
      }

      if (period == null) {
        throw new IllegalArgumentException("Refill period can not be null");
      }

      long refillPeriodNanos = period.toNanos();
      if (refillPeriodNanos < 1) {
        throw new IllegalArgumentException("Refill period should be positive");
      }

      return new Builder(id, capacity, tokens, period.dividedBy(tokens));
    }

    /**
     * Configures batch/interval refilling.
     *
     * @param tokens number of tokens added at each interval
     * @param period interval between refills
     * @return builder used to configure initial tokens
     */
    public Builder refillIntervally(long tokens, Duration period) {
      if (period == null) {
        throw new IllegalArgumentException("Refill period can not be null");
      }

      if (tokens < 1) {
        throw new IllegalArgumentException("Refill tokens must be positive");
      }

      long refillPeriodNanos = period.toNanos();
      if (refillPeriodNanos < 1) {
        throw new IllegalArgumentException("Refill period should be positive");
      }

      return new Builder(id, capacity, tokens, period);
    }

  }

  /** Final builder step for configuring initial token count. */
  public static class Builder {

    private final String id;
    private final long capacity;
    private final long refillRate;
    private final Duration refillPeriod;
    private long initial;

    private Builder(String id, long capacity, long refillRate, Duration refillPeriod) {
      this.id = id;
      this.initial = capacity;
      this.capacity = capacity;
      this.refillRate = refillRate;
      this.refillPeriod = refillPeriod;
    }

    /**
     * Sets the number of tokens available when the bucket is first created.
     *
     * @param initialTokens initial token count
     * @return this builder
     */
    public Builder initialTokens(long initialTokens) {
      if (initialTokens < 0) {
        throw new IllegalArgumentException("Initial tokens count must be positive");
      }

      this.initial = initialTokens;
      return this;
    }

    /**
     * Creates the bandwidth definition.
     *
     * @return configured bandwidth
     */
    public Bandwidth build() {
      return new Bandwidth(this);
    }
  }
}

