package br.com.cactus.core.domain

import br.com.cactus.core.exception.InsufficientStockException
import java.math.BigDecimal
import java.time.Instant

data class Product(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sku: String,
    val name: String,
    val description: String,
    val price: BigDecimal,
    val stock: Int,
    val version: Long = 0,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    init {
        require(sku.isNotBlank()) { "SKU cannot be blank" }
        require(name.isNotBlank()) { "Name cannot be blank" }
        require(price > BigDecimal.ZERO) { "Price must be positive" }
        require(stock >= 0) { "Stock cannot be negative" }
    }

    fun reserveStock(quantity: Int): Product {
        if (stock < quantity) {
            throw InsufficientStockException(id, name, stock, quantity)
        }
        return copy(stock = stock - quantity, updatedAt = Instant.now())
    }

    fun restoreStock(quantity: Int) = copy(
        stock = stock + quantity,
        updatedAt = Instant.now()
    )

    fun updatePrice(newPrice: BigDecimal): Product {
        require(newPrice > BigDecimal.ZERO) { "Price must be positive" }
        return copy(price = newPrice, updatedAt = Instant.now())
    }
}
