package br.com.cactus.adapter.inbound.rest.mapper

import br.com.cactus.adapter.inbound.rest.dto.*
import br.com.cactus.core.domain.Order
import br.com.cactus.core.domain.Product
import br.com.cactus.core.domain.User
import br.com.cactus.core.ports.input.*

// User Mappers
fun User.toResponse() = UserResponse(
    id = id,
    name = name,
    email = email,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun CreateUserRequest.toCommand() = CreateUserCommand(
    name = name,
    email = email
)

fun UpdateUserRequest.toCommand(id: java.util.UUID) = UpdateUserCommand(
    id = id,
    name = name,
    email = email
)

// Product Mappers
fun Product.toResponse() = ProductResponse(
    id = id,
    sku = sku,
    name = name,
    description = description,
    price = price,
    stock = stock,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun CreateProductRequest.toCommand() = CreateProductCommand(
    sku = sku,
    name = name,
    description = description,
    price = price,
    initialStock = initialStock
)

fun UpdateProductRequest.toCommand(id: String) = UpdateProductCommand(
    id = id,
    name = name,
    description = description,
    price = price
)

fun StockOperationRequest.toReserveCommand(productId: String) = ReserveStockCommand(
    productId = productId,
    quantity = quantity,
    reason = reason
)

fun StockOperationRequest.toRestoreCommand(productId: String) = RestoreStockCommand(
    productId = productId,
    quantity = quantity,
    reason = reason
)

fun UpdateStockRequest.toCommand(productId: String) = UpdateStockCommand(
    productId = productId,
    newStock = newStock,
    reason = reason
)

// Order Mappers
fun Order.toResponse() = OrderResponse(
    id = id,
    userId = userId,
    items = items.map { item ->
        OrderItemResponse(
            productId = item.productId,
            productName = item.productName,
            quantity = item.quantity,
            unitPrice = item.unitPrice,
            subtotal = item.subtotal
        )
    },
    status = status,
    total = total,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun CreateOrderRequest.toCommand() = CreateOrderCommand(
    userId = userId,
    items = items.map { OrderItemCommand(it.productId, it.quantity) }
)

// Paged Result Mapper
fun <T, R> PagedResult<T>.toResponse(mapper: (T) -> R) = PagedResponse(
    content = content.map(mapper),
    page = page,
    size = size,
    totalElements = totalElements,
    totalPages = totalPages,
    hasNext = page < totalPages - 1,
    hasPrevious = page > 0
)
