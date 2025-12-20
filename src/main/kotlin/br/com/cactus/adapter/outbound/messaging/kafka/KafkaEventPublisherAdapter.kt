package br.com.cactus.adapter.outbound.messaging.kafka

import br.com.cactus.core.domain.DomainEvent
import br.com.cactus.core.ports.output.KafkaEventPublisher
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaEventPublisherAdapter(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    @Value("\${kafka.topics.domain-events:domain-events}")
    private val defaultTopic: String
) : KafkaEventPublisher {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun publish(event: DomainEvent, topic: String?) {
        val targetTopic = topic ?: defaultTopic
        val payload = objectMapper.writeValueAsString(event)

        logger.debug("Publishing event to Kafka topic '$targetTopic': ${event.eventType}")

        kafkaTemplate.send(targetTopic, event.eventId, payload)
            .toCompletableFuture()
            .await()

        logger.info("Event published to Kafka: ${event.eventType} (${event.eventId})")
    }

    override suspend fun publishAll(events: List<DomainEvent>, topic: String?) {
        events.forEach { publish(it, topic) }
    }

    override suspend fun publishWithKey(event: DomainEvent, key: String, topic: String) {
        val payload = objectMapper.writeValueAsString(event)

        logger.debug("Publishing event to Kafka topic '$topic' with key '$key': ${event.eventType}")

        kafkaTemplate.send(topic, key, payload)
            .toCompletableFuture()
            .await()

        logger.info("Event published to Kafka with key: ${event.eventType} (key: $key)")
    }
}
