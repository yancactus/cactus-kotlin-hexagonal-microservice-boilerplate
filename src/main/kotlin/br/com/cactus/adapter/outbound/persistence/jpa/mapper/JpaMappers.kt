package br.com.cactus.adapter.outbound.persistence.jpa.mapper

import br.com.cactus.adapter.outbound.persistence.jpa.entity.OrderEntity
import br.com.cactus.adapter.outbound.persistence.jpa.entity.OrderItemEntity
import br.com.cactus.adapter.outbound.persistence.jpa.entity.UserEntity
import br.com.cactus.core.domain.Order
import br.com.cactus.core.domain.OrderItem
import br.com.cactus.core.domain.User

// User Mappers
fun UserEntity.toDomain() = User(
    id = id,
    name = name,
    email = email,
    active = active,
    version = version,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun User.toEntity() = UserEntity(
    id = id,
    name = name,
    email = email,
    active = active,
    version = version,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// Order Mappers
fun OrderEntity.toDomain() = Order(
    id = id,
    userId = userId,
    items = items.map { it.toDomain() },
    status = status,
    version = version,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Order.toEntity() = OrderEntity(
    id = id,
    userId = userId,
    status = status,
    items = items.map { it.toEntity() }.toMutableList(),
    version = version,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun OrderItemEntity.toDomain() = OrderItem(
    productId = productId,
    productName = productName,
    quantity = quantity,
    unitPrice = unitPrice
)

fun OrderItem.toEntity() = OrderItemEntity(
    productId = productId,
    productName = productName,
    quantity = quantity,
    unitPrice = unitPrice,
    subtotal = subtotal
)
