package br.com.cactus.adapter.outbound.cache

import br.com.cactus.core.config.LockKeys
import br.com.cactus.core.exception.DistributedLockException
import br.com.cactus.core.ports.output.DistributedLockPort
import br.com.cactus.core.ports.output.LockHandle
import br.com.cactus.core.ports.output.MultiLockHandle
import br.com.cactus.core.ports.output.MultiLockPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

@Component
class RedisDistributedLockAdapter(
    private val redissonClient: RedissonClient
) : DistributedLockPort, MultiLockPort {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun tryLock(
        resourceId: String,
        waitTime: Duration,
        leaseTime: Duration
    ): LockHandle? = withContext(Dispatchers.IO) {
        val lock = redissonClient.getLock(LockKeys.forResource(resourceId))

        val acquired = lock.tryLock(
            waitTime.inWholeMilliseconds,
            leaseTime.inWholeMilliseconds,
            TimeUnit.MILLISECONDS
        )

        if (acquired) {
            logger.debug("Lock acquired for resource: $resourceId")
            RedisLockHandle(lock, resourceId)
        } else {
            logger.debug("Failed to acquire lock for resource: $resourceId")
            null
        }
    }

    override suspend fun lock(
        resourceId: String,
        waitTime: Duration,
        leaseTime: Duration
    ): LockHandle {
        return tryLock(resourceId, waitTime, leaseTime)
            ?: throw DistributedLockException(resourceId)
    }

    override suspend fun <T> withLock(
        resourceId: String,
        waitTime: Duration,
        leaseTime: Duration,
        block: suspend () -> T
    ): T {
        val handle = lock(resourceId, waitTime, leaseTime)
        return try {
            block()
        } finally {
            handle.unlock()
        }
    }

    override suspend fun tryLockAll(
        resourceIds: List<String>,
        waitTime: Duration,
        leaseTime: Duration
    ): MultiLockHandle? = withContext(Dispatchers.IO) {
        val locks = resourceIds.map { redissonClient.getLock(LockKeys.forResource(it)) }
        val multiLock = redissonClient.getMultiLock(*locks.toTypedArray())

        val acquired = multiLock.tryLock(
            waitTime.inWholeMilliseconds,
            leaseTime.inWholeMilliseconds,
            TimeUnit.MILLISECONDS
        )

        if (acquired) {
            logger.debug("Multi-lock acquired for resources: $resourceIds")
            RedisMultiLockHandle(multiLock, resourceIds)
        } else {
            logger.debug("Failed to acquire multi-lock for resources: $resourceIds")
            null
        }
    }

    override suspend fun <T> withLocks(
        resourceIds: List<String>,
        waitTime: Duration,
        leaseTime: Duration,
        block: suspend () -> T
    ): T {
        val handle = tryLockAll(resourceIds, waitTime, leaseTime)
            ?: throw DistributedLockException(resourceIds.joinToString())

        return try {
            block()
        } finally {
            handle.unlockAll()
        }
    }

    private inner class RedisLockHandle(
        private val lock: RLock,
        override val resourceId: String
    ) : LockHandle {

        override val isHeld: Boolean
            get() = lock.isHeldByCurrentThread

        override suspend fun extend(additionalTime: Duration): Boolean = withContext(Dispatchers.IO) {
            try {
                lock.lock(additionalTime.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                true
            } catch (e: Exception) {
                logger.warn("Failed to extend lock: $resourceId", e)
                false
            }
        }

        override suspend fun unlock() = withContext(Dispatchers.IO) {
            try {
                if (lock.isHeldByCurrentThread) {
                    lock.unlock()
                    logger.debug("Lock released for resource: $resourceId")
                }
            } catch (e: Exception) {
                logger.warn("Error releasing lock: $resourceId", e)
            }
            Unit
        }

        override fun close() {
            try {
                if (lock.isHeldByCurrentThread) {
                    lock.unlock()
                }
            } catch (e: Exception) {
                logger.warn("Error closing lock: $resourceId", e)
            }
        }
    }

    private inner class RedisMultiLockHandle(
        private val multiLock: RLock,
        override val resourceIds: List<String>
    ) : MultiLockHandle {

        override suspend fun unlockAll() = withContext(Dispatchers.IO) {
            try {
                if (multiLock.isHeldByCurrentThread) {
                    multiLock.unlock()
                    logger.debug("Multi-lock released for resources: $resourceIds")
                }
            } catch (e: Exception) {
                logger.warn("Error releasing multi-lock: $resourceIds", e)
            }
            Unit
        }

        override fun close() {
            try {
                if (multiLock.isHeldByCurrentThread) {
                    multiLock.unlock()
                }
            } catch (e: Exception) {
                logger.warn("Error closing multi-lock: $resourceIds", e)
            }
        }
    }
}
