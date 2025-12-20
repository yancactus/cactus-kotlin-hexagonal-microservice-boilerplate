package br.com.cactus.adapter.inbound.consumer.kafka

import br.com.cactus.core.config.EventTypes
import br.com.cactus.core.config.MetricNames
import br.com.cactus.core.domain.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
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
    private val meterRegistry: MeterRegistry
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
    }

    private fun handleStockUpdated(json: JsonNode) {
        val event = objectMapper.treeToValue(json, ProductStockUpdatedEvent::class.java)
        logger.info(
            "Processing ProductStockUpdatedEvent: productId=${event.productId}, " + "${event.previousStock} -> ${event.newStock}, reason=${event.reason}"
        )
    }

    private fun handleOrderCreated(json: JsonNode) {
        val event = objectMapper.treeToValue(json, OrderCreatedEvent::class.java)
        logger.info(
            "Processing OrderCreatedEvent: orderId=${event.orderId}, userId=${event.userId}, " + "items=${event.items.size}"
        )
    }

    private fun handleOrderStatusChanged(json: JsonNode) {
        val event = objectMapper.treeToValue(json, OrderStatusChangedEvent::class.java)
        logger.info(
            "Processing OrderStatusChangedEvent: orderId=${event.orderId}, " + "${event.previousStatus} -> ${event.newStatus}"
        )
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
