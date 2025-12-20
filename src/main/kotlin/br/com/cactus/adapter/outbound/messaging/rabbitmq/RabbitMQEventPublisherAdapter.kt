package br.com.cactus.adapter.outbound.messaging.rabbitmq

import br.com.cactus.core.domain.DomainEvent
import br.com.cactus.core.ports.output.RabbitMQEventPublisher
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class RabbitMQEventPublisherAdapter(
    private val rabbitTemplate: RabbitTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${rabbitmq.exchange.domain-events:domain-events-exchange}")
    private val defaultExchange: String,
    @Value("\${rabbitmq.queue.domain-events:domain-events-queue}")
    private val defaultQueue: String
) : RabbitMQEventPublisher {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun publish(event: DomainEvent, topic: String?) = withContext(Dispatchers.IO) {
        val queue = topic ?: defaultQueue
        val payload = objectMapper.writeValueAsBytes(event)

        val messageProperties = MessageProperties().apply {
            contentType = MessageProperties.CONTENT_TYPE_JSON
            messageId = event.eventId
            setHeader("eventType", event.eventType)
        }

        val message = Message(payload, messageProperties)

        logger.debug("Publishing event to RabbitMQ queue '$queue': ${event.eventType}")
        rabbitTemplate.send(queue, message)
        logger.info("Event published to RabbitMQ: ${event.eventType} (${event.eventId})")
    }

    override suspend fun publishAll(events: List<DomainEvent>, topic: String?) {
        events.forEach { publish(it, topic) }
    }

    override suspend fun publishToExchange(event: DomainEvent, exchange: String, routingKey: String) = withContext(Dispatchers.IO) {
        val payload = objectMapper.writeValueAsBytes(event)

        val messageProperties = MessageProperties().apply {
            contentType = MessageProperties.CONTENT_TYPE_JSON
            messageId = event.eventId
            setHeader("eventType", event.eventType)
        }

        val message = Message(payload, messageProperties)

        logger.debug("Publishing event to RabbitMQ exchange '$exchange' with routing key '$routingKey': ${event.eventType}")
        rabbitTemplate.send(exchange, routingKey, message)
        logger.info("Event published to RabbitMQ exchange: ${event.eventType} (exchange: $exchange, routingKey: $routingKey)")
    }
}
