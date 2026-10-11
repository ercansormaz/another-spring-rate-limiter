package dev.ercan.spring.rate.limiter.redis.config;

import dev.ercan.spring.rate.limiter.RateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RateLimiterRedisAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(RateLimiterRedisAutoConfiguration.class));

  @Test
  @DisplayName("Should create RateLimiter bean when RedisConnectionFactory is present")
  void shouldCreateRateLimiterWhenRedisConnectionFactoryIsPresent() {
    contextRunner
        .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
        .run(context -> {
          assertThat(context).hasSingleBean(RateLimiter.class);
        });
  }

  @Test
  @DisplayName("Should not create RateLimiter bean when RedisConnectionFactory is absent")
  void shouldNotCreateRateLimiterWhenRedisConnectionFactoryIsAbsent() {
    contextRunner
        .run(context -> {
          assertThat(context).doesNotHaveBean(RateLimiter.class);
        });
  }

  @Test
  @DisplayName("Should not overwrite existing RateLimiter bean")
  void shouldNotOverwriteExistingRateLimiterBean() {
    RateLimiter customRateLimiter = mock(RateLimiter.class);

    contextRunner
        .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
        .withBean(RateLimiter.class, () -> customRateLimiter)
        .run(context -> {
          assertThat(context).hasSingleBean(RateLimiter.class);
          assertThat(context.getBean(RateLimiter.class)).isSameAs(customRateLimiter);
        });
  }
}
