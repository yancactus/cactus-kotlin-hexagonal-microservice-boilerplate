package br.com.cactus.adapter.inbound.consumer

import br.com.cactus.core.domain.AuditAction
import br.com.cactus.core.domain.AuditLog
import br.com.cactus.core.domain.EntityType
import br.com.cactus.core.domain.OrderCreatedEvent
import br.com.cactus.core.domain.OrderStatusChangedEvent
import br.com.cactus.core.domain.ProductStockUpdatedEvent
import br.com.cactus.core.domain.UserCreatedEvent
import br.com.cactus.core.ports.output.AuditLogRepository
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class AuditEventListener(
    private val auditLogRepository: AuditLogRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Async
    @EventListener
    fun handleUserCreatedEvent(event: UserCreatedEvent) {
        logger.debug("Handling UserCreatedEvent: userId=${event.userId}")
        saveAuditLog(
            entityType = EntityType.USER,
            entityId = event.userId.toString(),
            action = AuditAction.CREATE,
            newValue = objectMapper.writeValueAsString(event),
            metadata = mapOf("email" to event.email)
        )
    }

    @Async
    @EventListener
    fun handleProductStockUpdatedEvent(event: ProductStockUpdatedEvent) {
        logger.debug("Handling ProductStockUpdatedEvent: productId=${event.productId}")
        saveAuditLog(
            entityType = EntityType.PRODUCT,
            entityId = event.productId,
            action = AuditAction.UPDATE,
            oldValue = objectMapper.writeValueAsString(mapOf("stock" to event.previousStock)),
            newValue = objectMapper.writeValueAsString(mapOf("stock" to event.newStock)),
            metadata = mapOf("reason" to event.reason)
        )
    }

    @Async
    @EventListener
    fun handleOrderCreatedEvent(event: OrderCreatedEvent) {
        logger.debug("Handling OrderCreatedEvent: orderId=${event.orderId}")
        saveAuditLog(
            entityType = EntityType.ORDER,
            entityId = event.orderId.toString(),
            action = AuditAction.CREATE,
            userId = event.userId.toString(),
            newValue = objectMapper.writeValueAsString(event),
            metadata = mapOf("itemCount" to event.items.size.toString())
        )
    }

    @Async
    @EventListener
    fun handleOrderStatusChangedEvent(event: OrderStatusChangedEvent) {
        logger.debug("Handling OrderStatusChangedEvent: orderId=${event.orderId}")
        saveAuditLog(
            entityType = EntityType.ORDER,
            entityId = event.orderId.toString(),
            action = AuditAction.UPDATE,
            oldValue = objectMapper.writeValueAsString(mapOf("status" to event.previousStatus)),
            newValue = objectMapper.writeValueAsString(mapOf("status" to event.newStatus)),
            metadata = mapOf(
                "previousStatus" to event.previousStatus.name,
                "newStatus" to event.newStatus.name
            )
        )
    }

    private fun saveAuditLog(
        entityType: EntityType,
        entityId: String,
        action: AuditAction,
        userId: String? = null,
        oldValue: String? = null,
        newValue: String? = null,
        metadata: Map<String, String> = emptyMap()
    ) {
        try {
            runBlocking {
                val auditLog = AuditLog.create(
                    entityType = entityType,
                    entityId = entityId,
                    action = action,
                    userId = userId,
                    oldValue = oldValue,
                    newValue = newValue,
                    metadata = metadata
                )
                auditLogRepository.save(auditLog)
                logger.info("Audit log saved: entityType=$entityType, entityId=$entityId, action=$action")
            }
        } catch (e: Exception) {
            logger.error("Failed to save audit log: entityType=$entityType, entityId=$entityId, action=$action", e)
        }
    }
}
