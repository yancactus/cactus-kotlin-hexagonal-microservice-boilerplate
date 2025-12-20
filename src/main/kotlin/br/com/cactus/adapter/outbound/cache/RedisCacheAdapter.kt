package br.com.cactus.adapter.outbound.cache

import br.com.cactus.core.ports.output.CachePort
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import kotlin.time.Duration
import kotlin.time.toJavaDuration

@Component
class RedisCacheAdapter(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) : CachePort {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun <T : Any> get(key: String, type: Class<T>): T? = withContext(Dispatchers.IO) {
        try {
            val value = redisTemplate.opsForValue().get(key)
            if (value != null) {
                logger.debug("Cache HIT for key: $key")
                objectMapper.readValue(value, type)
            } else {
                logger.debug("Cache MISS for key: $key")
                null
            }
        } catch (e: Exception) {
            logger.warn("Error reading from cache: $key", e)
            null
        }
    }

    override suspend fun <T : Any> set(key: String, value: T, ttl: Duration?) = withContext(Dispatchers.IO) {
        try {
            val serialized = objectMapper.writeValueAsString(value)
            if (ttl != null) {
                redisTemplate.opsForValue().set(key, serialized, ttl.toJavaDuration())
            } else {
                redisTemplate.opsForValue().set(key, serialized)
            }
            logger.debug("Cached value for key: $key (ttl: $ttl)")
        } catch (e: Exception) {
            logger.warn("Error writing to cache: $key", e)
        }
    }

    override suspend fun <T : Any> setIfAbsent(key: String, value: T, ttl: Duration?): Boolean = withContext(Dispatchers.IO) {
        try {
            val serialized = objectMapper.writeValueAsString(value)
            val result = if (ttl != null) {
                redisTemplate.opsForValue().setIfAbsent(key, serialized, ttl.toJavaDuration()) == true
            } else {
                redisTemplate.opsForValue().setIfAbsent(key, serialized) == true
            }
            logger.debug("SetIfAbsent for key: $key, result: $result")
            result
        } catch (e: Exception) {
            logger.warn("Error in setIfAbsent: $key", e)
            false
        }
    }

    override suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        try {
            redisTemplate.delete(key)
            logger.debug("Deleted cache key: $key")
        } catch (e: Exception) {
            logger.warn("Error deleting from cache: $key", e)
        }
        Unit
    }

    override suspend fun deleteByPattern(pattern: String) = withContext(Dispatchers.IO) {
        try {
            val keys = redisTemplate.keys(pattern)
            if (keys.isNotEmpty()) {
                redisTemplate.delete(keys)
                logger.debug("Deleted ${keys.size} keys matching pattern: $pattern")
            }
        } catch (e: Exception) {
            logger.warn("Error deleting by pattern: $pattern", e)
        }
        Unit
    }

    override suspend fun exists(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            redisTemplate.hasKey(key)
        } catch (e: Exception) {
            logger.warn("Error checking key existence: $key", e)
            false
        }
    }

    override suspend fun increment(key: String, delta: Long): Long = withContext(Dispatchers.IO) {
        try {
            redisTemplate.opsForValue().increment(key, delta) ?: 0L
        } catch (e: Exception) {
            logger.warn("Error incrementing key: $key", e)
            0L
        }
    }

    override suspend fun <T : Any> getOrPut(
        key: String,
        type: Class<T>,
        ttl: Duration?,
        compute: suspend () -> T
    ): T = withContext(Dispatchers.IO) {
        val cached = get(key, type)
        if (cached != null) {
            return@withContext cached
        }

        val computed = compute()
        set(key, computed, ttl)
        computed
    }
}
