package dev.ercan.spring.rate.limiter.redis.config;

import dev.ercan.spring.rate.limiter.RateLimiter;
import dev.ercan.spring.rate.limiter.consumer.TokenBucketConsumer;
import dev.ercan.spring.rate.limiter.redis.consumer.RedisTokenBucketConsumer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Automatically configures a Redis-backed {@link RateLimiter}. */
@AutoConfiguration
@AutoConfigureAfter(DataRedisAutoConfiguration.class)
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnBean(RedisConnectionFactory.class)
public class RateLimiterRedisAutoConfiguration {

    /**
     * Default constructor for Spring to instantiate the configuration class.
     */
    public RateLimiterRedisAutoConfiguration() {
    }


  /**
   * Creates the default rate limiter when Redis is available and no custom
   * {@link RateLimiter} bean has been configured.
   *
   * @param connectionFactory Redis connection factory
   * @return Redis-backed rate limiter
   */
  @Bean
  @ConditionalOnMissingBean
  public RateLimiter rateLimiterRedisProxy(RedisConnectionFactory connectionFactory) {
    StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
    TokenBucketConsumer consumer = new RedisTokenBucketConsumer(redisTemplate);
    return new RateLimiter(consumer);
  }

}
