package br.com.cactus.core.ports.output

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface DistributedLockPort {

    suspend fun tryLock(
        resourceId: String,
        waitTime: Duration = 5.seconds,
        leaseTime: Duration = 30.seconds
    ): LockHandle?

    suspend fun lock(
        resourceId: String,
        waitTime: Duration = 5.seconds,
        leaseTime: Duration = 30.seconds
    ): LockHandle

    suspend fun <T> withLock(
        resourceId: String,
        waitTime: Duration = 5.seconds,
        leaseTime: Duration = 30.seconds,
        block: suspend () -> T
    ): T
}

interface LockHandle : AutoCloseable {
    val resourceId: String
    val isHeld: Boolean

    suspend fun extend(additionalTime: Duration): Boolean

    suspend fun unlock()

    override fun close() {
    }
}

interface MultiLockPort {

    suspend fun tryLockAll(
        resourceIds: List<String>,
        waitTime: Duration = 5.seconds,
        leaseTime: Duration = 30.seconds
    ): MultiLockHandle?

    suspend fun <T> withLocks(
        resourceIds: List<String>,
        waitTime: Duration = 5.seconds,
        leaseTime: Duration = 30.seconds,
        block: suspend () -> T
    ): T
}

interface MultiLockHandle : AutoCloseable {
    val resourceIds: List<String>
    suspend fun unlockAll()
}
