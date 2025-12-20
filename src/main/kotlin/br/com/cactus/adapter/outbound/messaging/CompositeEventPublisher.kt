package br.com.cactus.adapter.outbound.messaging

import br.com.cactus.core.domain.DomainEvent
import br.com.cactus.core.ports.output.EventPublisher
import br.com.cactus.core.ports.output.KafkaEventPublisher
import br.com.cactus.core.ports.output.RabbitMQEventPublisher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Component
@Primary
class CompositeEventPublisher(
    private val kafkaPublisher: KafkaEventPublisher,
    private val rabbitMQPublisher: RabbitMQEventPublisher,
    @Value("\${messaging.publish-to-kafka:true}")
    private val publishToKafka: Boolean,
    @Value("\${messaging.publish-to-rabbitmq:true}")
    private val publishToRabbitMQ: Boolean
) : EventPublisher {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun publish(event: DomainEvent, topic: String?): Unit = coroutineScope {
        logger.debug("Publishing event ${event.eventType} to messaging systems " +
            "(kafka=$publishToKafka, rabbitmq=$publishToRabbitMQ)")

        val kafkaJob = if (publishToKafka) {
            async {
                try {
                    kafkaPublisher.publish(event, topic)
                } catch (e: Exception) {
                    logger.error("Failed to publish to Kafka: ${event.eventType}", e)
                }
            }
        } else null

        val rabbitJob = if (publishToRabbitMQ) {
            async {
                try {
                    rabbitMQPublisher.publish(event, topic)
                } catch (e: Exception) {
                    logger.error("Failed to publish to RabbitMQ: ${event.eventType}", e)
                }
            }
        } else null

        kafkaJob?.await()
        rabbitJob?.await()
        Unit
    }

    override suspend fun publishAll(events: List<DomainEvent>, topic: String?) {
        events.forEach { publish(it, topic) }
    }
}
