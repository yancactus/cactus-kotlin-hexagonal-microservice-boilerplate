package br.com.cactus.core.domain

import br.com.cactus.core.exception.InvalidOrderStateException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class Order(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val items: List<OrderItem>,
    val status: OrderStatus = OrderStatus.PENDING,
    val version: Long = 0,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    val total: BigDecimal
        get() = items.sumOf { it.subtotal }

    init {
        require(items.isNotEmpty()) { "Order must have at least one item" }
    }

    fun confirm(): Order {
        if (status != OrderStatus.PENDING) {
            throw InvalidOrderStateException(id, status, OrderStatus.CONFIRMED)
        }
        return copy(status = OrderStatus.CONFIRMED, updatedAt = Instant.now())
    }

    fun cancel(): Order {
        if (status !in listOf(OrderStatus.PENDING, OrderStatus.CONFIRMED)) {
            throw InvalidOrderStateException(id, status, OrderStatus.CANCELLED)
        }
        return copy(status = OrderStatus.CANCELLED, updatedAt = Instant.now())
    }

    fun complete(): Order {
        if (status != OrderStatus.CONFIRMED) {
            throw InvalidOrderStateException(id, status, OrderStatus.COMPLETED)
        }
        return copy(status = OrderStatus.COMPLETED, updatedAt = Instant.now())
    }
}

data class OrderItem(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal
) {
    val subtotal: BigDecimal = unitPrice.multiply(BigDecimal(quantity))

    init {
        require(quantity > 0) { "Quantity must be positive" }
        require(unitPrice > BigDecimal.ZERO) { "Unit price must be positive" }
    }
}

enum class OrderStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED
}
