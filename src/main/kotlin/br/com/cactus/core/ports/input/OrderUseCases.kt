package br.com.cactus.core.ports.input

import br.com.cactus.core.domain.Order
import br.com.cactus.core.domain.OrderStatus
import java.math.BigDecimal
import java.util.UUID

interface CreateOrderUseCase {
    suspend fun execute(command: CreateOrderCommand): Order
}

interface GetOrderUseCase {
    suspend fun execute(id: UUID): Order
}

interface ListOrdersUseCase {
    suspend fun execute(query: ListOrdersQuery): PagedResult<Order>
}

interface ListUserOrdersUseCase {
    suspend fun execute(userId: UUID, query: ListOrdersQuery): PagedResult<Order>
}

/**
 * Confirms an order with distributed lock to ensure atomicity
 * when reserving stock across products.
 */
interface ConfirmOrderUseCase {
    suspend fun execute(orderId: UUID): Order
}

/**
 * Cancels an order and restores stock if necessary.
 */
interface CancelOrderUseCase {
    suspend fun execute(orderId: UUID): Order
}

/**
 * Completes an order (after delivery/fulfillment).
 */
interface CompleteOrderUseCase {
    suspend fun execute(orderId: UUID): Order
}

data class CreateOrderCommand(
    val userId: UUID,
    val items: List<OrderItemCommand>
)

data class OrderItemCommand(
    val productId: String,
    val quantity: Int
)

data class ListOrdersQuery(
    val page: Int = 0,
    val size: Int = 20,
    val status: OrderStatus? = null
)

// Response DTOs for order details
data class OrderWithDetails(
    val order: Order,
    val items: List<OrderItemWithProduct>
)

data class OrderItemWithProduct(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val subtotal: BigDecimal
)
