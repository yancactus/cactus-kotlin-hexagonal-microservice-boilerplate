package br.com.cactus.adapter.inbound.rest.dto

import br.com.cactus.core.domain.OrderStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateOrderRequest(
    val userId: UUID,

    @field:NotEmpty(message = "Order must have at least one item")
    @field:Valid
    val items: List<OrderItemRequest>
)

data class OrderItemRequest(
    @field:NotBlank(message = "Product ID is required")
    val productId: String,

    @field:Min(value = 1, message = "Quantity must be at least 1")
    val quantity: Int
)

data class OrderResponse(
    val id: UUID,
    val userId: UUID,
    val items: List<OrderItemResponse>,
    val status: OrderStatus,
    val total: BigDecimal,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class OrderItemResponse(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val subtotal: BigDecimal
)
