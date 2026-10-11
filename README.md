# Another Spring Rate Limiter

Redis-backed token-bucket rate limiting for Spring Boot applications.

The project is distributed as a Spring Boot starter and is designed to make a shared, distributed rate limiter available with minimal setup. Once Spring Data Redis is on the classpath and Redis is configured, inject `RateLimiter` and start creating buckets—there is no rate-limiter-specific configuration required.

The API is programmatic: inject `RateLimiter`, build a `Bucket`, and call consume/estimate methods. There is no annotation or servlet-filter integration in this project.

> The Redis starter is the primary integration point documented here. The core module is kept separate so that the rate-limiting model can also be reused with another storage implementation via `TokenBucketConsumer`.

## Features

- Zero rate-limiter-specific configuration through Spring Boot auto-configuration
- Redis-backed, distributed token-bucket rate limiting
- Compatible with Redis standalone, Sentinel, and Cluster deployments through Spring Data Redis
- Single or multiple bandwidth limits per bucket
- Atomic Redis Lua-script execution for consume, estimate, refill, and reset operations
- Consumption and estimation results with remaining tokens and the next refill time
- Java 17 and Spring Boot 4.1 compatible
- MIT licensed

## Requirements

- Java 17+
- Spring Boot 4.1.x
- Spring Data Redis on the application classpath
- Redis

## Versioning

This project aligns its version numbers with the supported Spring Boot release line:

- **`MAJOR.MINOR`** (e.g., `4.0`): Matches the compatible Spring Boot version (`4.0.x`).
- **`PATCH`** (e.g., `0` in `4.0.0`): Reserved for bug fixes and feature releases of this library.

| Library Version | Compatible Spring Boot |
| :--- | :--- |
| `4.0.x` | `4.0.x` |
| `4.1.x` | `4.1.x` |

## Installation

Add the Redis starter to your Maven project:

```xml
<dependency>
    <groupId>dev.ercan</groupId>
    <artifactId>spring-rate-limiter-redis-starter</artifactId>
    <version>4.1.0</version>
</dependency>
```

The starter depends on Spring Data Redis with `provided` scope, so the application must also bring Redis support, for example:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

Configure the Redis connection as usual. For a standalone instance:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

No additional rate-limiter properties are required. Auto-configuration activates only when a `RedisConnectionFactory` bean is available.

## Quick start

Inject `RateLimiter` into a Spring bean. Reuse bandwidth definitions; build a bucket per key when you need to consume:

```java
import dev.ercan.spring.rate.limiter.RateLimiter;
import dev.ercan.spring.rate.limiter.model.Bandwidth;
import dev.ercan.spring.rate.limiter.model.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class ApiService {

    private static final Bandwidth PER_SECOND = Bandwidth.builder("per-second")
            .capacity(10)
            .refillGreedy(10, Duration.ofSeconds(1))
            .build();

    private final RateLimiter rateLimiter;

    public ApiService(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public boolean canCall(String userId) {
        Bucket bucket = rateLimiter.builder()
                .key("api:user:" + userId)
                .addLimit(PER_SECOND)
                .build();

        return bucket.tryConsume();
    }
}
```

`Bucket` is a lightweight handle over the key and limits; Redis holds the shared state. The first call creates that state. All application instances connected to the same Redis deployment share it.

By default, a new bucket starts with `capacity` tokens. Override the cold-start balance with `initialTokens`:

```java
Bandwidth perSecond = Bandwidth.builder("per-second")
        .capacity(10)
        .refillGreedy(10, Duration.ofSeconds(1))
        .initialTokens(0)
        .build();
```

## Multiple limits

Add more than one bandwidth to enforce several limits atomically. The request is accepted only when all configured limits have enough tokens:

```java
Bandwidth perSecond = Bandwidth.builder("per-second")
        .capacity(10)
        .refillGreedy(10, Duration.ofSeconds(1))
        .build();

Bandwidth perDay = Bandwidth.builder("per-day")
        .capacity(10_000)
        .refillIntervally(10_000, Duration.ofDays(1))
        .build();

Bucket bucket = rateLimiter.builder()
        .key("api:user:" + userId)
        .addLimit(perSecond)
        .addLimit(perDay)
        .build();

boolean consumed = bucket.tryConsume();
```

Each bandwidth must have a unique identifier within a bucket. `refillGreedy` refills the configured tokens continuously at the calculated interval, while `refillIntervally` adds the configured number of tokens at each interval.

## Available operations

```java
boolean allowed = bucket.tryConsume();
boolean allowed = bucket.tryConsume(5);

ConsumptionResult result = bucket.tryConsumeAndGet();
EstimationResult estimate = bucket.estimateAbilityToConsume(5);

bucket.addTokens(3);
bucket.reset();
```

`ConsumptionResult` exposes:

- `consumed` — whether the tokens were taken
- `remainingTokens` — tokens left after the attempt
- `refillAt` — when the bucket is expected to gain tokens again

`EstimationResult` exposes the same shape with `canBeConsumed` instead of `consumed`, and does not change bucket state. These fields are useful for HTTP 429 responses and `Retry-After` style headers.

## Redis deployment modes

The starter uses Spring Data Redis and consumes the application’s `RedisConnectionFactory`. Therefore the same integration works with the Redis deployment mode already configured by Spring Boot:

- Standalone
- Sentinel
- Cluster

For Redis Cluster, bucket keys include a hash tag based on the bucket key (`rl:tb:{<bucket-key>}:<bandwidth-id>`). This keeps the bandwidth keys belonging to the same logical bucket in the same hash slot, which allows multi-bandwidth Lua operations to execute atomically.

Redis keys are given a TTL based on the time needed to refill the bucket from empty to capacity for that bandwidth. Idle buckets therefore expire instead of lingering forever; active use refreshes the TTL.

## How it works

Rate-limit state is stored in Redis and updated by Lua scripts. This keeps the token calculation and state mutation together on the Redis server, avoiding race conditions between concurrent application instances. The starter automatically registers a `RateLimiter` bean when a `RedisConnectionFactory` is available and does not replace an existing `RateLimiter` bean.

## Custom storage

To use another backend, implement `TokenBucketConsumer` in the core module and register your own `RateLimiter` bean. Auto-configuration steps aside when a `RateLimiter` is already present:

```java
@Bean
public RateLimiter rateLimiter(TokenBucketConsumer customConsumer) {
    return new RateLimiter(customConsumer);
}
```

The consumer must keep multi-bandwidth consume/estimate operations atomic for the guarantees described above.

## Benchmark

The benchmark compares this project with [Bucket4j](https://github.com/bucket4j/bucket4j) using the same Redis Cluster environment. Both implementations were tested with 100 concurrent threads, one or three bandwidths, and `tryConsume` and `tryConsumeAndGet` operations across 1, 10, 50, and 100 distinct keys.

### Environment

- Intel MacBook Pro
- 2.3 GHz 8-Core Intel Core i9
- 16 GB RAM
- JMH 1.37
- OpenJDK 25.0.4, 64-Bit Server VM
- Redis 8.10.2 running in Docker
- Redis Cluster mode with 3 masters and 3 replicas in a single container
- Spring Boot 4.1.1
- Bucket4j 8.21.0 with Lettuce
- Spring Boot Redis starter default client settings

JMH was configured with one warmup iteration, one 10-second measurement iteration, and one fork. The results should therefore be interpreted as a controlled comparative benchmark on the same machine, rather than as a production capacity guarantee.

### Summary

| Workload | This project | Bucket4j | This project p99 | Bucket4j p99 | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| 1 distinct key | 10.7k–13.1k ops/s | 461–610 ops/s | 13–16 ms | 757–999 ms | This project leads significantly |
| 10 distinct keys | 8.1k–9.8k ops/s | 2.8k–4.1k ops/s | 18–27 ms | 291–375 ms | This project leads |
| 50 distinct keys | 8.1k–10.0k ops/s | 7.7k–13.1k ops/s | 18–37 ms | 82–117 ms | Bucket4j leads in some workloads |
| 100 distinct keys | 7.2k–8.3k ops/s | 10.4k–15.3k ops/s | 17–30 ms | 48–72 ms | Bucket4j leads in throughput |

The p99 columns show the minimum–maximum p99 sample latency across the four benchmark combinations for each key count (single/multiple bandwidth and `consume`/`consumeAndGet`).

The complete JMH output is available in [benchmark-results.txt](spring-rate-limiter-redis-starter/benchmark-results.txt).

These results reflect the different implementation strategies. Bucket4j uses a CAS-based approach for its Redis-backed state updates, while this project performs the rate-limit calculation and state mutation inside Redis Lua scripts. Under high contention on a small number of keys, the Lua approach avoids repeated CAS retries and remains more stable. With a larger number of independent keys, Bucket4j achieves higher aggregate throughput in several scenarios.

The benchmark also indicates a tail-latency trade-off. This project generally has lower p99 latency, especially under contention, while Bucket4j can provide higher aggregate throughput when the workload is distributed across many keys. There is no single overall winner; the appropriate choice depends on the workload shape and operational requirements.

### Choosing between the implementations

Choose this project when:

- the workload has high contention on a small or moderate number of keys;
- predictable p99 latency is more important than maximum aggregate throughput;
- Redis Lua-based atomicity and a single server-side operation are desirable;
- the application is Spring Boot and Redis-centric and a lightweight starter integration is preferred.

Choose Bucket4j when:

- the workload contains many independent keys;
- maximum aggregate throughput is the primary objective;
- Bucket4j's broader ecosystem, maturity, or additional integration options are important;
- the application can accept the latency characteristics of the selected storage and concurrency strategy.

This benchmark uses a six-node Redis Cluster topology inside a single Docker container. It does not model production node placement, network latency, hardware isolation, or failure behavior. For production sizing, the benchmark should be repeated with the target Redis topology, client configuration, JVM version, and workload distribution.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
