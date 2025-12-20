package br.com.cactus.core.ports.input

import br.com.cactus.core.domain.Product

interface CreateProductUseCase {
    suspend fun execute(command: CreateProductCommand): Product
}

interface GetProductUseCase {
    suspend fun execute(id: String): Product
}

interface UpdateProductUseCase {
    suspend fun execute(command: UpdateProductCommand): Product
}

interface DeleteProductUseCase {
    suspend fun execute(id: String)
}

interface ListProductsUseCase {
    suspend fun execute(query: ListProductsQuery): PagedResult<Product>
}

/**
 * Use case for reserving stock with distributed lock.
 * This ensures atomicity across multiple instances.
 */
interface ReserveStockUseCase {
    suspend fun execute(command: ReserveStockCommand): Product
}

/**
 * Use case for restoring stock (e.g., after order cancellation).
 */
interface RestoreStockUseCase {
    suspend fun execute(command: RestoreStockCommand): Product
}

/**
 * Use case for updating stock with pessimistic locking.
 * Used for high-contention scenarios.
 */
interface UpdateStockWithLockUseCase {
    suspend fun execute(command: UpdateStockCommand): Product
}

data class CreateProductCommand(
    val sku: String,
    val name: String,
    val description: String,
    val price: java.math.BigDecimal,
    val initialStock: Int
)

data class UpdateProductCommand(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    val price: java.math.BigDecimal? = null
)

data class ListProductsQuery(
    val page: Int = 0,
    val size: Int = 20,
    val minStock: Int? = null
)

data class ReserveStockCommand(
    val productId: String,
    val quantity: Int,
    val reason: String
)

data class RestoreStockCommand(
    val productId: String,
    val quantity: Int,
    val reason: String
)

data class UpdateStockCommand(
    val productId: String,
    val newStock: Int,
    val reason: String
)
