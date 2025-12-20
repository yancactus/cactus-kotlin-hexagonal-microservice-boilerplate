package br.com.cactus.adapter.inbound.scheduler

import br.com.cactus.core.config.SchedulerConfig
import br.com.cactus.core.domain.OrderStatus
import br.com.cactus.core.ports.input.CancelOrderUseCase
import br.com.cactus.core.ports.output.OrderRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.runBlocking
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

@Component
class OrderExpirationScheduler(
    private val orderRepository: OrderRepository,
    private val cancelOrderUseCase: CancelOrderUseCase,
    private val meterRegistry: MeterRegistry,
    @Value("\${order.expiration.pending-hours:24}") private val pendingExpirationHours: Long,
    @Value("\${order.expiration.batch-size:100}") private val batchSize: Int,
    @Value("\${order.expiration.enabled:true}") private val enabled: Boolean
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${order.expiration.cron:0 0 * * * *}")
    @SchedulerLock(
        name = SchedulerConfig.ORDER_EXPIRATION_LOCK_NAME,
        lockAtMostFor = SchedulerConfig.ORDER_EXPIRATION_LOCK_AT_MOST,
        lockAtLeastFor = SchedulerConfig.ORDER_EXPIRATION_LOCK_AT_LEAST
    )
    fun cancelExpiredPendingOrders() {
        if (!enabled) {
            logger.debug("Order expiration job is disabled")
            return
        }

        val startTime = System.nanoTime()
        var cancelledCount = 0
        var errorCount = 0

        logger.info("Starting order expiration check (expiration: ${pendingExpirationHours}h, batch: $batchSize)")

        try {
            runBlocking {
                val expirationThreshold = Instant.now().minus(Duration.ofHours(pendingExpirationHours))

                var page = 0
                var hasMore = true

                while (hasMore) {
                    val orders = orderRepository.findAll(page, batchSize, OrderStatus.PENDING)

                    val expiredOrders = orders.content.filter { it.createdAt.isBefore(expirationThreshold) }

                    if (expiredOrders.isEmpty()) {
                        hasMore = false
                        continue
                    }

                    for (order in expiredOrders) {
                        try {
                            logger.info("Cancelling expired order: ${order.id} (created: ${order.createdAt})")
                            cancelOrderUseCase.execute(order.id)
                            cancelledCount++
                        } catch (e: Exception) {
                            logger.error("Failed to cancel expired order ${order.id}", e)
                            errorCount++
                        }
                    }

                    page++
                    hasMore = orders.content.size == batchSize
                }
            }

            logger.info("Order expiration check completed: cancelled=$cancelledCount, errors=$errorCount")

        } catch (e: Exception) {
            logger.error("Order expiration job failed", e)
            throw e
        } finally {
            val duration = System.nanoTime() - startTime

            Timer.builder("order.expiration.job.duration").register(meterRegistry)
                .record(duration, TimeUnit.NANOSECONDS)

            meterRegistry.counter("order.expiration.cancelled.count").increment(cancelledCount.toDouble())
            meterRegistry.counter("order.expiration.errors.count").increment(errorCount.toDouble())
        }
    }

    @Scheduled(cron = "\${order.stats.cron:0 0 2 * * *}")
    @SchedulerLock(
        name = SchedulerConfig.ORDER_DAILY_STATS_LOCK_NAME,
        lockAtMostFor = SchedulerConfig.ORDER_STATS_LOCK_AT_MOST,
        lockAtLeastFor = SchedulerConfig.ORDER_STATS_LOCK_AT_LEAST
    )
    fun generateDailyOrderStats() {
        if (!enabled) {
            return
        }

        logger.info("Starting daily order statistics generation")

        try {
            runBlocking {
                val pendingOrders = orderRepository.findAll(0, 1, OrderStatus.PENDING)
                val confirmedOrders = orderRepository.findAll(0, 1, OrderStatus.CONFIRMED)
                val completedOrders = orderRepository.findAll(0, 1, OrderStatus.COMPLETED)
                val cancelledOrders = orderRepository.findAll(0, 1, OrderStatus.CANCELLED)

                logger.info(
                    "Daily Order Stats: pending=${pendingOrders.totalElements}, " +
                        "confirmed=${confirmedOrders.totalElements}, " +
                        "completed=${completedOrders.totalElements}, " +
                        "cancelled=${cancelledOrders.totalElements}"
                )
            }
        } catch (e: Exception) {
            logger.error("Daily order stats generation failed", e)
        }
    }
}
