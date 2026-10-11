package dev.ercan.spring.rate.limiter.consumer;

import dev.ercan.spring.rate.limiter.model.Bandwidth;
import dev.ercan.spring.rate.limiter.model.ConsumptionResult;
import dev.ercan.spring.rate.limiter.model.EstimationResult;
import java.util.List;

/**
 * Performs token-bucket operations for one or more bandwidth limits.
 *
 * <p>Implementations are responsible for storing bucket state and for making
 * operations atomic when multiple bandwidths are used.</p>
 */
public interface TokenBucketConsumer {

  /**
   * Attempts to consume tokens from all supplied bandwidths atomically.
   *
   * @param key bucket key
   * @param tokensToConsume number of tokens to consume
   * @param bandwidths bandwidth limits
   * @return {@code true} when all limits allowed the consumption
   */
  boolean tryConsume(String key, long tokensToConsume, List<Bandwidth> bandwidths);

  /**
   * Consumes tokens from all supplied bandwidths and returns the resulting state.
   *
   * @param key bucket key
   * @param tokensToConsume number of tokens to consume
   * @param bandwidths bandwidth limits
   * @return consumption outcome and refill information
   */
  ConsumptionResult tryConsumeAndGet(String key, long tokensToConsume, List<Bandwidth> bandwidths);

  /**
   * Estimates consumption against all supplied bandwidths without changing state.
   *
   * @param key bucket key
   * @param tokensToConsume number of tokens to consume
   * @param bandwidths bandwidth limits
   * @return estimation of the consumption outcome
   */
  EstimationResult estimateAbilityToConsume(String key, long tokensToConsume, List<Bandwidth> bandwidths);

  /**
   * Adds tokens to all supplied buckets.
   *
   * @param key bucket key
   * @param tokens number of tokens to add
   * @param bandwidths bandwidth limits
   */
  void addTokens(String key, long tokens, List<Bandwidth> bandwidths);

  /**
   * Removes the state of all supplied buckets.
   *
   * @param key bucket key
   * @param bandwidths bandwidth limits
   */
  void reset(String key, List<Bandwidth> bandwidths);

}
