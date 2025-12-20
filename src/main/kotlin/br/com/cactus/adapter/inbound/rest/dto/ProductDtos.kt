package br.com.cactus.adapter.inbound.rest.dto

import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.Instant

data class CreateProductRequest(
    @field:NotBlank(message = "SKU is required")
    @field:Size(min = 3, max = 50, message = "SKU must be between 3 and 50 characters")
    val sku: String,

    @field:NotBlank(message = "Name is required")
    @field:Size(min = 2, max = 200, message = "Name must be between 2 and 200 characters")
    val name: String,

    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    val description: String = "",

    @field:NotNull(message = "Price is required")
    @field:DecimalMin(value = "0.01", message = "Price must be greater than 0")
    val price: BigDecimal,

    @field:Min(value = 0, message = "Initial stock cannot be negative")
    val initialStock: Int = 0
)

data class UpdateProductRequest(
    @field:Size(min = 2, max = 200, message = "Name must be between 2 and 200 characters")
    val name: String? = null,

    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    val description: String? = null,

    @field:DecimalMin(value = "0.01", message = "Price must be greater than 0")
    val price: BigDecimal? = null
)

data class StockOperationRequest(
    @field:Min(value = 1, message = "Quantity must be at least 1")
    val quantity: Int,

    @field:NotBlank(message = "Reason is required")
    val reason: String
)

data class UpdateStockRequest(
    @field:Min(value = 0, message = "Stock cannot be negative")
    val newStock: Int,

    @field:NotBlank(message = "Reason is required")
    val reason: String
)

data class ProductResponse(
    val id: String,
    val sku: String,
    val name: String,
    val description: String,
    val price: BigDecimal,
    val stock: Int,
    val createdAt: Instant,
    val updatedAt: Instant
)
