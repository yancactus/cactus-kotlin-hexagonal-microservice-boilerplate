package br.com.cactus.config

import io.lettuce.core.ClientOptions
import io.lettuce.core.SocketOptions
import io.lettuce.core.TimeoutOptions
import io.lettuce.core.cluster.ClusterClientOptions
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisClusterConfiguration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
@EnableCaching
class RedisConfig(
    @Value("\${spring.data.redis.host:localhost}")
    private val redisHost: String,

    @Value("\${spring.data.redis.port:6379}")
    private val redisPort: Int,

    @Value("\${spring.data.redis.password:}")
    private val redisPassword: String,

    @Value("\${spring.data.redis.cluster.nodes:}")
    private val clusterNodes: List<String>,

    @Value("\${spring.data.redis.cluster.max-redirects:3}")
    private val maxRedirects: Int,

    @Value("\${spring.data.redis.lettuce.pool.max-active:16}")
    private val maxActive: Int,

    @Value("\${spring.data.redis.lettuce.pool.min-idle:4}")
    private val minIdle: Int,

    @Value("\${spring.data.redis.timeout:3000}")
    private val timeout: Long
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Lettuce connection factory for standalone Redis.
     */
    @Bean
    @Profile("!redis-cluster")
    fun standaloneLettuceConnectionFactory(): LettuceConnectionFactory {
        logger.info("Configuring Redis Standalone mode: $redisHost:$redisPort")

        val standaloneConfig = RedisStandaloneConfiguration(redisHost, redisPort)
        if (redisPassword.isNotBlank()) {
            standaloneConfig.setPassword(redisPassword)
        }

        val clientConfig = LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofMillis(timeout))
            .clientOptions(
                ClientOptions.builder()
                    .socketOptions(
                        SocketOptions.builder()
                            .connectTimeout(Duration.ofMillis(timeout))
                            .keepAlive(true)
                            .build()
                    )
                    .timeoutOptions(TimeoutOptions.enabled())
                    .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                    .build()
            )
            .build()

        return LettuceConnectionFactory(standaloneConfig, clientConfig)
    }

    /**
     * Lettuce connection factory for Redis Cluster.
     * Activated when spring.profiles.active includes 'redis-cluster'.
     */
    @Bean
    @Profile("redis-cluster")
    fun clusterLettuceConnectionFactory(): LettuceConnectionFactory {
        logger.info("Configuring Redis Cluster mode with nodes: $clusterNodes")

        val clusterConfig = RedisClusterConfiguration(clusterNodes)
        clusterConfig.maxRedirects = maxRedirects
        if (redisPassword.isNotBlank()) {
            clusterConfig.setPassword(redisPassword)
        }

        // Cluster topology refresh options for automatic failover detection
        val topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
            .enablePeriodicRefresh(Duration.ofSeconds(30))
            .enableAllAdaptiveRefreshTriggers()
            .adaptiveRefreshTriggersTimeout(Duration.ofSeconds(30))
            .build()

        val clientConfig = LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofMillis(timeout))
            .clientOptions(
                ClusterClientOptions.builder()
                    .topologyRefreshOptions(topologyRefreshOptions)
                    .socketOptions(
                        SocketOptions.builder()
                            .connectTimeout(Duration.ofMillis(timeout))
                            .keepAlive(true)
                            .build()
                    )
                    .timeoutOptions(TimeoutOptions.enabled())
                    .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                    .autoReconnect(true)
                    .build()
            )
            .build()

        return LettuceConnectionFactory(clusterConfig, clientConfig)
    }

    @Bean
    fun stringRedisTemplate(connectionFactory: RedisConnectionFactory): StringRedisTemplate {
        return StringRedisTemplate(connectionFactory)
    }

    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory): CacheManager {
        val cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(GenericJackson2JsonRedisSerializer())
            )
            .disableCachingNullValues()

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(cacheConfig)
            .withCacheConfiguration("users", cacheConfig.entryTtl(Duration.ofMinutes(30)))
            .withCacheConfiguration("products", cacheConfig.entryTtl(Duration.ofHours(1)))
            .withCacheConfiguration("orders", cacheConfig.entryTtl(Duration.ofMinutes(15)))
            .build()
    }

    /**
     * Redisson client for distributed locking (Standalone mode).
     */
    @Bean(destroyMethod = "shutdown")
    @Profile("!redis-cluster")
    fun standaloneRedissonClient(): RedissonClient {
        logger.info("Configuring Redisson for Standalone mode")

        val config = Config()
        val address = "redis://$redisHost:$redisPort"

        val singleServerConfig = config.useSingleServer()
            .setAddress(address)
            .setConnectionMinimumIdleSize(minIdle)
            .setConnectionPoolSize(maxActive)
            .setRetryAttempts(3)
            .setRetryInterval(1500)
            .setTimeout(timeout.toInt())
            .setConnectTimeout(timeout.toInt())

        if (redisPassword.isNotBlank()) {
            singleServerConfig.setPassword(redisPassword)
        }

        return Redisson.create(config)
    }

    /**
     * Redisson client for distributed locking (Cluster mode).
     */
    @Bean(destroyMethod = "shutdown")
    @Profile("redis-cluster")
    fun clusterRedissonClient(): RedissonClient {
        logger.info("Configuring Redisson for Cluster mode")

        val config = Config()
        val nodeAddresses = clusterNodes.map { "redis://$it" }.toTypedArray()

        val clusterServersConfig = config.useClusterServers()
            .addNodeAddress(*nodeAddresses)
            .setMasterConnectionMinimumIdleSize(minIdle)
            .setMasterConnectionPoolSize(maxActive)
            .setSlaveConnectionMinimumIdleSize(minIdle)
            .setSlaveConnectionPoolSize(maxActive)
            .setRetryAttempts(3)
            .setRetryInterval(1500)
            .setTimeout(timeout.toInt())
            .setConnectTimeout(timeout.toInt())

        if (redisPassword.isNotBlank()) {
            clusterServersConfig.setPassword(redisPassword)
        }

        return Redisson.create(config)
    }
}
