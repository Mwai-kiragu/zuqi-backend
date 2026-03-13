package com.zuqi.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.support.CompositeCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * In-memory cache for AI/ML model objects.
     * Tribuo models contain complex internal state (OffsetDateTime, native handles, etc.)
     * that cannot be JSON-serialized into Redis. A simple ConcurrentMap is correct here:
     * models are large, deserialization is expensive, and we want JVM-local reuse.
     */
    @Bean
    public ConcurrentMapCacheManager aiModelCacheManager() {
        return new ConcurrentMapCacheManager("aiModels");
    }

    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // User sessions - shorter TTL
        cacheConfigurations.put("user-sessions", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // Product catalog - medium TTL
        cacheConfigurations.put("products", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // Credit scores - longer TTL since computation is expensive
        cacheConfigurations.put("credit-scores", defaultConfig.entryTtl(Duration.ofHours(1)));

        // AI configurations - short TTL to pick up changes
        cacheConfigurations.put("ai-configs", defaultConfig.entryTtl(Duration.ofMinutes(15)));

        // Merchants
        cacheConfigurations.put("merchants", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // Embeddings - long TTL since they're deterministic and expensive
        cacheConfigurations.put("embeddings", defaultConfig.entryTtl(Duration.ofHours(24)));

        // Merchant features - 24 hour TTL since feature computation is expensive
        cacheConfigurations.put("merchantFeatures", defaultConfig.entryTtl(Duration.ofHours(24)));

        // Payment features - 6 hour TTL for per-payment anomaly detection
        cacheConfigurations.put("paymentFeatures", defaultConfig.entryTtl(Duration.ofHours(6)));

        // Merchant payment trends - 24 hour TTL for distress prediction features
        cacheConfigurations.put("merchantPaymentTrends", defaultConfig.entryTtl(Duration.ofHours(24)));

        // Demand features - 24 hour TTL, refreshed nightly by batch job
        cacheConfigurations.put("demandFeatures", defaultConfig.entryTtl(Duration.ofHours(24)));

        // Inventory features - 6 hour TTL for shrinkage detection and stockout prediction
        cacheConfigurations.put("inventoryFeatures", defaultConfig.entryTtl(Duration.ofHours(6)));

        // Sales rep features - 24 hour TTL for performance tracking
        cacheConfigurations.put("salesRepFeatures", defaultConfig.entryTtl(Duration.ofHours(24)));

        // Chat conversation history - 30 min TTL (active session window)
        // Stores pre-formatted history strings keyed by conversationId.
        // Evicted on every new message save to stay fresh.
        cacheConfigurations.put("chat-history", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // Assistant business context - 5 min TTL per distributorId
        // Pre-fetched DB snapshot (9 data points) injected into chat prompts.
        // Short TTL so data stays reasonably fresh without hitting DB every message.
        cacheConfigurations.put("assistant-context", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        RedisCacheManager redisCacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();

        // Composite: aiModels → in-memory first; everything else → Redis
        CompositeCacheManager composite = new CompositeCacheManager(
                aiModelCacheManager(), redisCacheManager);
        composite.setFallbackToNoOpCache(false);
        return composite;
    }
}
