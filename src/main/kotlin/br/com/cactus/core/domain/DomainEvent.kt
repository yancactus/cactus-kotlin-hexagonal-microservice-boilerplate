package br.com.cactus.core.domain

import java.time.Instant
import java.util.UUID

interface DomainEvent {
    val eventId: String
    val eventType: String
    val occurredAt: Instant
}

data class UserCreatedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    val userId: UUID,
    val email: String
) : DomainEvent {
    override val eventType = "USER_CREATED"
}

data class ProductStockUpdatedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    val productId: String,
    val previousStock: Int,
    val newStock: Int,
    val reason: String
) : DomainEvent {
    override val eventType = "PRODUCT_STOCK_UPDATED"
}

data class OrderCreatedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    val orderId: UUID,
    val userId: UUID,
    val items: List<OrderItemEvent>
) : DomainEvent {
    override val eventType = "ORDER_CREATED"
}

data class OrderItemEvent(
    val productId: String,
    val quantity: Int
)

data class OrderStatusChangedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    val orderId: UUID,
    val previousStatus: OrderStatus,
    val newStatus: OrderStatus
) : DomainEvent {
    override val eventType = "ORDER_STATUS_CHANGED"
}
