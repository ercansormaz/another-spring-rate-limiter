package dev.ercan.spring.rate.limiter.model;

import java.time.Instant;

/**
 * Result of a token consumption attempt.
 *
 * @param consumed whether the consumption succeeded
 * @param remainingTokens number of tokens remaining after the attempt
 * @param refillAt time when the bucket will be refilled
 */
public record ConsumptionResult(boolean consumed, long remainingTokens, Instant refillAt) {
}
