package br.com.cactus.core.exception

import java.util.UUID

sealed class DomainException(
    override val message: String,
    override val cause: Throwable? = null
) : RuntimeException(message, cause)

class EntityNotFoundException(
    entityType: String,
    id: Any
) : DomainException("$entityType with id '$id' not found")

class InsufficientStockException(
    val productId: String,
    val productName: String,
    val availableStock: Int,
    val requestedQuantity: Int
) : DomainException(
    "Insufficient stock for product '$productName' (id: $productId). " +
        "Available: $availableStock, Requested: $requestedQuantity"
)

class InvalidOrderStateException(
    val orderId: UUID,
    val currentStatus: Any,
    val targetStatus: Any
) : DomainException(
    "Cannot transition order '$orderId' from '$currentStatus' to '$targetStatus'"
)

class ConcurrencyException(
    entityType: String,
    id: Any,
    cause: Throwable? = null
) : DomainException(
    "$entityType with id '$id' was modified by another transaction. Please retry.",
    cause
)

class DuplicateEntityException(
    entityType: String,
    field: String,
    value: Any
) : DomainException("$entityType with $field '$value' already exists")

class DistributedLockException(
    resourceId: String,
    cause: Throwable? = null
) : DomainException("Failed to acquire lock for resource '$resourceId'", cause)

class ValidationException(
    message: String
) : DomainException(message)
