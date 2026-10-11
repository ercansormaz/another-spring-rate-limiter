package dev.ercan.spring.rate.limiter.redis.consumer;

import dev.ercan.spring.rate.limiter.consumer.TokenBucketConsumer;
import dev.ercan.spring.rate.limiter.model.Bandwidth;
import dev.ercan.spring.rate.limiter.model.ConsumptionResult;
import dev.ercan.spring.rate.limiter.model.EstimationResult;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Redis-backed implementation of {@link TokenBucketConsumer}.
 *
 * <p>Operations are executed by Lua scripts so a consumption involving
 * multiple bandwidths is handled atomically by Redis.</p>
 */
public class RedisTokenBucketConsumer implements TokenBucketConsumer {

  private static final String BUCKET_KEY_FORMAT = "rl:tb:{%s}:%s";

  private static final ConsumptionResult IMPOSSIBLE_CONSUMPTION = new ConsumptionResult(false, 0, null);
  private static final EstimationResult IMPOSSIBLE_ESTIMATION = new EstimationResult(false, 0, null);

  private static final RedisScript<Long> SINGLE_CONSUME_SCRIPT =
      loadScript("scripts/single_consume.lua", Long.class);
  private static final RedisScript<List> SINGLE_CONSUME_AND_GET_SCRIPT =
      loadScript("scripts/single_consume_and_get.lua", List.class);
  private static final RedisScript<List> SINGLE_ESTIMATE_SCRIPT =
      loadScript("scripts/single_estimate.lua", List.class);
  private static final RedisScript<Long> SINGLE_ADD_TOKEN_SCRIPT =
      loadScript("scripts/single_add_token.lua", Long.class);
  private static final RedisScript<Long> MULTI_CONSUME_SCRIPT =
      loadScript("scripts/multi_consume.lua", Long.class);
  private static final RedisScript<List> MULTI_CONSUME_AND_GET_SCRIPT =
      loadScript("scripts/multi_consume_and_get.lua", List.class);
  private static final RedisScript<List> MULTI_ESTIMATE_SCRIPT =
      loadScript("scripts/multi_estimate.lua", List.class);
  private static final RedisScript<Long> MULTI_ADD_TOKEN_SCRIPT =
      loadScript("scripts/multi_add_token.lua", Long.class);

  private final RedisTemplate<String, String> redisTemplate;

  /**
   * Creates a Redis-backed token bucket consumer.
   *
   * @param redisTemplate template used to execute the bucket Lua scripts
   * @throws NullPointerException if {@code redisTemplate} is null
   */
  @SuppressWarnings("unchecked")
  public RedisTokenBucketConsumer(RedisTemplate<?, ?> redisTemplate) {
    Objects.requireNonNull(redisTemplate, "redisTemplate cannot be null");
    this.redisTemplate = (RedisTemplate<String, String>) redisTemplate;
  }

  @Override
  public boolean tryConsume(String key, long tokensToConsume, List<Bandwidth> bandwidths) {
    if (bandwidths.size() == 1) {
      return tryConsumeSingleBandwidth(key, tokensToConsume, bandwidths.get(0));
    }

    return tryConsumeMultiBandwidth(key, tokensToConsume, bandwidths);
  }

  private boolean tryConsumeSingleBandwidth(String key, long tokensToConsume, Bandwidth bandwidth) {
    if (tokensToConsume > bandwidth.getCapacity()) {
      return false;
    }

    List<String> keys = List.of(buildBucketKey(key, bandwidth.getId()));
    List<String> args = buildSingleArgs(tokensToConsume, bandwidth);

    return executeBooleanScript(SINGLE_CONSUME_SCRIPT, keys, args);
  }

  private boolean tryConsumeMultiBandwidth(String key, long tokensToConsume, List<Bandwidth> bandwidths) {
    if (exceedsAnyCapacity(tokensToConsume, bandwidths)) {
      return false;
    }

    List<String> keys = buildMultiKeys(key, bandwidths);
    List<String> args = buildMultiArgs(tokensToConsume, bandwidths);

    return executeBooleanScript(MULTI_CONSUME_SCRIPT, keys, args);
  }

  @Override
  public ConsumptionResult tryConsumeAndGet(String key, long tokensToConsume, List<Bandwidth> bandwidths) {
    if (bandwidths.size() == 1) {
      return tryConsumeAndGetSingleBandwidth(key, tokensToConsume, bandwidths.get(0));
    }

    return tryConsumeAndGetMultiBandwidth(key, tokensToConsume, bandwidths);
  }

  private ConsumptionResult tryConsumeAndGetSingleBandwidth(String key, long tokensToConsume, Bandwidth bandwidth) {
    if (tokensToConsume > bandwidth.getCapacity()) {
      return IMPOSSIBLE_CONSUMPTION;
    }

    List<String> keys = List.of(buildBucketKey(key, bandwidth.getId()));
    List<String> args = buildSingleArgs(tokensToConsume, bandwidth);

    return executeConsumptionResultScript(SINGLE_CONSUME_AND_GET_SCRIPT, keys, args);
  }

  private ConsumptionResult tryConsumeAndGetMultiBandwidth(String key, long tokensToConsume, List<Bandwidth> bandwidths) {
    if (exceedsAnyCapacity(tokensToConsume, bandwidths)) {
      return IMPOSSIBLE_CONSUMPTION;
    }

    List<String> keys = buildMultiKeys(key, bandwidths);
    List<String> args = buildMultiArgs(tokensToConsume, bandwidths);

    return executeConsumptionResultScript(MULTI_CONSUME_AND_GET_SCRIPT, keys, args);
  }

  @Override
  public EstimationResult estimateAbilityToConsume(String key, long tokensToConsume, List<Bandwidth> bandwidths) {
    if (bandwidths.size() == 1) {
      return estimateAbilityToConsumeSingleBandwidth(key, tokensToConsume, bandwidths.get(0));
    }

    return estimateAbilityToConsumeMultiBandwidth(key, tokensToConsume, bandwidths);
  }


  private EstimationResult estimateAbilityToConsumeSingleBandwidth(String key, long tokensToConsume, Bandwidth bandwidth) {
    if (tokensToConsume > bandwidth.getCapacity()) {
      return IMPOSSIBLE_ESTIMATION;
    }

    List<String> keys = List.of(buildBucketKey(key, bandwidth.getId()));
    List<String> args = buildSingleArgs(tokensToConsume, bandwidth);

    return executeEstimationResultScript(SINGLE_ESTIMATE_SCRIPT, keys, args);
  }

  private EstimationResult estimateAbilityToConsumeMultiBandwidth(String key, long tokensToConsume, List<Bandwidth> bandwidths) {
    if (exceedsAnyCapacity(tokensToConsume, bandwidths)) {
      return IMPOSSIBLE_ESTIMATION;
    }

    List<String> keys = buildMultiKeys(key, bandwidths);
    List<String> args = buildMultiArgs(tokensToConsume, bandwidths);

    return executeEstimationResultScript(MULTI_ESTIMATE_SCRIPT, keys, args);
  }

  @Override
  public void addTokens(String key, long tokens, List<Bandwidth> bandwidths) {
    if (bandwidths.size() == 1) {
      addTokensSingleBandwidth(key, tokens, bandwidths.get(0));
      return;
    }

    addTokensMultiBandwidth(key, tokens, bandwidths);
  }

  private void addTokensSingleBandwidth(String key, long tokens, Bandwidth bandwidth) {
    List<String> keys = List.of(buildBucketKey(key, bandwidth.getId()));
    List<String> args = buildSingleArgs(tokens, bandwidth);

    executeCommandScript(SINGLE_ADD_TOKEN_SCRIPT, keys, args);
  }

  private void addTokensMultiBandwidth(String key, long tokens, List<Bandwidth> bandwidths) {
    List<String> keys = buildMultiKeys(key, bandwidths);
    List<String> args = buildMultiArgs(tokens, bandwidths);

    executeCommandScript(MULTI_ADD_TOKEN_SCRIPT, keys, args);
  }

  @Override
  public void reset(String key, List<Bandwidth> bandwidths) {
    List<String> keys = bandwidths.stream()
        .map(bandwidth -> buildBucketKey(key, bandwidth.getId()))
        .toList();

    redisTemplate.delete(keys);
  }

  private boolean exceedsAnyCapacity(long tokens, List<Bandwidth> bandwidths) {
    return bandwidths.stream().anyMatch(bandwidth -> tokens > bandwidth.getCapacity());
  }

  private String buildBucketKey(String key, String bandwidthId) {
    return String.format(BUCKET_KEY_FORMAT, key, bandwidthId);
  }

  private List<String> buildMultiKeys(String key, List<Bandwidth> bandwidths) {
    List<String> keys = new ArrayList<>(bandwidths.size());
    for (Bandwidth bandwidth : bandwidths) {
      keys.add(buildBucketKey(key, bandwidth.getId()));
    }
    return keys;
  }

  private List<String> buildSingleArgs(long tokens, Bandwidth bandwidth) {
    List<String> args = new ArrayList<>(7);
    args.add(Long.toString(Instant.now().toEpochMilli()));
    args.add(Long.toString(tokens));
    appendBandwidthArgs(args, bandwidth);
    return args;
  }

  private List<String> buildMultiArgs(long tokens, List<Bandwidth> bandwidths) {
    List<String> args = new ArrayList<>((bandwidths.size() * 5) + 2);
    args.add(Long.toString(Instant.now().toEpochMilli()));
    args.add(Long.toString(tokens));

    for (Bandwidth bandwidth : bandwidths) {
      appendBandwidthArgs(args, bandwidth);
    }
    return args;
  }

  private void appendBandwidthArgs(List<String> args, Bandwidth bandwidth) {
    args.add(Long.toString(bandwidth.getInitial()));
    args.add(Long.toString(bandwidth.getCapacity()));
    args.add(Long.toString(bandwidth.getRefillRate()));
    args.add(Long.toString(bandwidth.getRefillPeriod().toMillis()));
    args.add(Long.toString(getExpireInMillis(bandwidth)));
  }

  private long getExpireInMillis(Bandwidth bandwidth) {
    long intervals = (bandwidth.getCapacity() + bandwidth.getRefillRate() - 1) / bandwidth.getRefillRate();
    return bandwidth.getRefillPeriod().multipliedBy(intervals).toMillis();
  }

  private boolean executeBooleanScript(RedisScript<Long> script, List<String> keys, List<String> args) {
    Long result = redisTemplate.execute(script, keys, args.toArray());
    if (result == null) {
      throw new IllegalStateException("Invalid result from redis");
    }
    return result == 1L;
  }

  private void executeCommandScript(RedisScript<Long> script, List<String> keys, List<String> args) {
    Long result = redisTemplate.execute(script, keys, args.toArray());
    if (result == null) {
      throw new IllegalStateException("Invalid result from redis");
    }
  }

  @SuppressWarnings("unchecked")
  private ConsumptionResult executeConsumptionResultScript(RedisScript<List> script, List<String> keys, List<String> args) {
    List<Long> result = redisTemplate.execute(script, keys, args.toArray());
    if (result == null || result.size() < 3) {
      throw new IllegalStateException("Invalid result from redis");
    }
    return new ConsumptionResult(result.get(0) == 1L, result.get(1), Instant.ofEpochMilli(result.get(2)));
  }

  @SuppressWarnings("unchecked")
  private EstimationResult executeEstimationResultScript(RedisScript<List> script, List<String> keys, List<String> args) {
    List<Long> result = redisTemplate.execute(script, keys, args.toArray());
    if (result == null || result.size() < 3) {
      throw new IllegalStateException("Invalid result from redis");
    }
    return new EstimationResult(result.get(0) == 1L, result.get(1), Instant.ofEpochMilli(result.get(2)));
  }

  private static <T> RedisScript<T> loadScript(String path, Class<T> resultType) {
    DefaultRedisScript<T> script = new DefaultRedisScript<>();
    script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
    script.setResultType(resultType);
    return script;
  }

}
