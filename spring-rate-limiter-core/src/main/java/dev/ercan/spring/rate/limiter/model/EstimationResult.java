package dev.ercan.spring.rate.limiter.model;

import java.time.Instant;

/**
 * Result of checking whether a token consumption could succeed.
 *
 * @param canBeConsumed whether the consumption would succeed
 * @param remainingTokens number of tokens that would remain after consumption
 * @param refillAt time when the bucket would be refilled
 */
public record EstimationResult(boolean canBeConsumed, long remainingTokens, Instant refillAt) {

}
