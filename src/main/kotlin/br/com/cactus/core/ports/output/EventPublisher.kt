package br.com.cactus.core.ports.output

import br.com.cactus.core.domain.DomainEvent

interface EventPublisher {
    suspend fun publish(event: DomainEvent, topic: String? = null)

    suspend fun publishAll(events: List<DomainEvent>, topic: String? = null)
}

interface KafkaEventPublisher : EventPublisher {
    suspend fun publishWithKey(event: DomainEvent, key: String, topic: String)
}

interface RabbitMQEventPublisher : EventPublisher {
    suspend fun publishToExchange(event: DomainEvent, exchange: String, routingKey: String)
}
