package br.com.cactus.core.ports.output

import kotlin.time.Duration

interface CachePort {

    suspend fun <T : Any> get(key: String, type: Class<T>): T?

    suspend fun <T : Any> set(key: String, value: T, ttl: Duration? = null)

    suspend fun <T : Any> setIfAbsent(key: String, value: T, ttl: Duration? = null): Boolean

    suspend fun delete(key: String)

    suspend fun deleteByPattern(pattern: String)

    suspend fun exists(key: String): Boolean

    suspend fun increment(key: String, delta: Long = 1): Long

    suspend fun <T : Any> getOrPut(
        key: String,
        type: Class<T>,
        ttl: Duration? = null,
        compute: suspend () -> T
    ): T
}

suspend inline fun <reified T : Any> CachePort.get(key: String): T? = get(key, T::class.java)

suspend inline fun <reified T : Any> CachePort.getOrPut(
    key: String,
    ttl: Duration? = null,
    noinline compute: suspend () -> T
): T = getOrPut(key, T::class.java, ttl, compute)
