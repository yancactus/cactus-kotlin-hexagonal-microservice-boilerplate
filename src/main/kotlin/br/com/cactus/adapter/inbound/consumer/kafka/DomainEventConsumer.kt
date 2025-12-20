package br.com.cactus.adapter.inbound.consumer.kafka

import br.com.cactus.core.config.EventTypes
import br.com.cactus.core.config.MetricNames
import br.com.cactus.core.domain.*
import br.com.cactus.core.ports.output.AuditLogRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class DomainEventConsumer(
    private val objectMapper: ObjectMapper,
    private val meterRegistry: MeterRegistry,
    private val auditLogRepository: AuditLogRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["\${kafka.topics.domain-events:domain-events}"],
        groupId = "\${kafka.consumer.group-id:cactus-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consumeDomainEvent(
        @Payload payload: String,
        @Header(KafkaHeaders.RECEIVED_KEY, required = false) key: String?,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment
    ) {
        val startTime = System.nanoTime()

        try {
            logger.debug("Received Kafka message: partition=$partition, offset=$offset, key=$key")

            val jsonNode = objectMapper.readTree(payload)
            val eventType = jsonNode.get("eventType")?.asText()

            when (eventType) {
                EventTypes.USER_CREATED -> handleUserCreated(jsonNode)
                EventTypes.PRODUCT_CREATED -> handleProductCreated(jsonNode)
                EventTypes.PRODUCT_STOCK_UPDATED -> handleStockUpdated(jsonNode)
                EventTypes.ORDER_CREATED -> handleOrderCreated(jsonNode)
                EventTypes.ORDER_STATUS_CHANGED -> handleOrderStatusChanged(jsonNode)
                else -> logger.warn("Unknown event type: $eventType")
            }

            acknowledgment.acknowledge()

            recordMetrics(eventType ?: "unknown", startTime, true)

        } catch (e: Exception) {
            logger.error("Error processing Kafka message: partition=$partition, offset=$offset", e)
            recordMetrics("error", startTime, false)
        }
    }

    private fun handleUserCreated(json: JsonNode) {
        val event = objectMapper.treeToValue(json, UserCreatedEvent::class.java)
        logger.info("Processing UserCreatedEvent: userId=${event.userId}, email=${event.email}")

        saveAuditLog(
            entityType = EntityType.USER,
            entityId = event.userId.toString(),
            action = AuditAction.CREATE,
            newValue = objectMapper.writeValueAsString(event),
            metadata = mapOf("email" to event.email)
        )
    }

    private fun handleProductCreated(json: JsonNode) {
        val event = objectMapper.treeToValue(json, ProductCreatedEvent::class.java)
        logger.info("Processing ProductCreatedEvent: productId=${event.productId}, sku=${event.sku}")

        saveAuditLog(
            entityType = EntityType.PRODUCT,
            entityId = event.productId,
            action = AuditAction.CREATE,
            newValue = objectMapper.writeValueAsString(event),
            metadata = mapOf(
                "sku" to event.sku,
                "name" to event.name,
                "price" to event.price.toString(),
                "stock" to event.stock.toString()
            )
        )
    }

    private fun handleStockUpdated(json: JsonNode) {
        val event = objectMapper.treeToValue(json, ProductStockUpdatedEvent::class.java)
        logger.info(
            "Processing ProductStockUpdatedEvent: productId=${event.productId}, " +
                "${event.previousStock} -> ${event.newStock}, reason=${event.reason}"
        )

        saveAuditLog(
            entityType = EntityType.PRODUCT,
            entityId = event.productId,
            action = AuditAction.UPDATE,
            oldValue = objectMapper.writeValueAsString(mapOf("stock" to event.previousStock)),
            newValue = objectMapper.writeValueAsString(mapOf("stock" to event.newStock)),
            metadata = mapOf("reason" to event.reason)
        )
    }

    private fun handleOrderCreated(json: JsonNode) {
        val event = objectMapper.treeToValue(json, OrderCreatedEvent::class.java)
        logger.info(
            "Processing OrderCreatedEvent: orderId=${event.orderId}, userId=${event.userId}, " +
                "items=${event.items.size}"
        )

        saveAuditLog(
            entityType = EntityType.ORDER,
            entityId = event.orderId.toString(),
            action = AuditAction.CREATE,
            userId = event.userId.toString(),
            newValue = objectMapper.writeValueAsString(event),
            metadata = mapOf("itemCount" to event.items.size.toString())
        )
    }

    private fun handleOrderStatusChanged(json: JsonNode) {
        val event = objectMapper.treeToValue(json, OrderStatusChangedEvent::class.java)
        logger.info(
            "Processing OrderStatusChangedEvent: orderId=${event.orderId}, " +
                "${event.previousStatus} -> ${event.newStatus}"
        )

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

    private fun recordMetrics(eventType: String, startTime: Long, success: Boolean) {
        val duration = System.nanoTime() - startTime
        Timer.builder(MetricNames.KAFKA_CONSUMER_PROCESSING_TIME)
            .tag(MetricNames.EVENT_TYPE_TAG, eventType)
            .tag(MetricNames.SUCCESS_TAG, success.toString())
            .register(meterRegistry)
            .record(duration, TimeUnit.NANOSECONDS)
    }
}
