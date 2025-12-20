package br.com.cactus.adapter.inbound.consumer.rabbitmq

import br.com.cactus.core.domain.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.rabbitmq.client.Channel
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.support.AmqpHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class RabbitMQEventConsumer(
    private val objectMapper: ObjectMapper,
    private val meterRegistry: MeterRegistry
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(
        queues = ["\${rabbitmq.queue.domain-events:domain-events-queue}"],
        containerFactory = "rabbitListenerContainerFactory"
    )
    fun consumeDomainEvent(
        message: Message,
        channel: Channel,
        @Header(AmqpHeaders.DELIVERY_TAG) deliveryTag: Long
    ) {
        val startTime = System.nanoTime()
        var eventType = "unknown"

        try {
            val payload = String(message.body)
            logger.debug("Received RabbitMQ message: deliveryTag=$deliveryTag")

            val jsonNode = objectMapper.readTree(payload)
            eventType = jsonNode.get("eventType")?.asText() ?: "unknown"

            when (eventType) {
                "USER_CREATED" -> handleUserCreated(jsonNode)
                "PRODUCT_STOCK_UPDATED" -> handleStockUpdated(jsonNode)
                "ORDER_CREATED" -> handleOrderCreated(jsonNode)
                "ORDER_STATUS_CHANGED" -> handleOrderStatusChanged(jsonNode)
                else -> logger.warn("Unknown event type: $eventType")
            }

            channel.basicAck(deliveryTag, false)
            recordMetrics(eventType, startTime, true)

        } catch (e: Exception) {
            logger.error("Error processing RabbitMQ message: deliveryTag=$deliveryTag", e)
            recordMetrics(eventType, startTime, false)

            channel.basicNack(deliveryTag, false, false)
        }
    }

    private fun handleUserCreated(json: JsonNode) {
        val event = objectMapper.treeToValue(json, UserCreatedEvent::class.java)
        logger.info("[RabbitMQ] Processing UserCreatedEvent: userId=${event.userId}")
    }

    private fun handleStockUpdated(json: JsonNode) {
        val event = objectMapper.treeToValue(json, ProductStockUpdatedEvent::class.java)
        logger.info("[RabbitMQ] Processing ProductStockUpdatedEvent: productId=${event.productId}")
    }

    private fun handleOrderCreated(json: JsonNode) {
        val event = objectMapper.treeToValue(json, OrderCreatedEvent::class.java)
        logger.info("[RabbitMQ] Processing OrderCreatedEvent: orderId=${event.orderId}")
    }

    private fun handleOrderStatusChanged(json: JsonNode) {
        val event = objectMapper.treeToValue(json, OrderStatusChangedEvent::class.java)
        logger.info("[RabbitMQ] Processing OrderStatusChangedEvent: orderId=${event.orderId}")
    }

    private fun recordMetrics(eventType: String, startTime: Long, success: Boolean) {
        val duration = System.nanoTime() - startTime
        Timer.builder("rabbitmq.consumer.processing.time")
            .tag("event_type", eventType)
            .tag("success", success.toString())
            .register(meterRegistry)
            .record(duration, TimeUnit.NANOSECONDS)
    }
}
