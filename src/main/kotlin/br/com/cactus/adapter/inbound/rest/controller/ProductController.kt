package br.com.cactus.adapter.inbound.rest.controller

import br.com.cactus.adapter.inbound.rest.dto.*
import br.com.cactus.adapter.inbound.rest.mapper.*
import br.com.cactus.core.ports.input.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Product and stock management endpoints")
class ProductController(
    private val createProductUseCase: CreateProductUseCase,
    private val getProductUseCase: GetProductUseCase,
    private val updateProductUseCase: UpdateProductUseCase,
    private val deleteProductUseCase: DeleteProductUseCase,
    private val listProductsUseCase: ListProductsUseCase,
    private val reserveStockUseCase: ReserveStockUseCase,
    private val restoreStockUseCase: RestoreStockUseCase,
    private val updateStockWithLockUseCase: UpdateStockWithLockUseCase
) {

    @PostMapping
    @Operation(summary = "Create a new product")
    suspend fun createProduct(
        @Valid @RequestBody request: CreateProductRequest
    ): ResponseEntity<ProductResponse> {
        val product = createProductUseCase.execute(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(product.toResponse())
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    suspend fun getProduct(@PathVariable id: String): ResponseEntity<ProductResponse> {
        val product = getProductUseCase.execute(id)
        return ResponseEntity.ok(product.toResponse())
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update product details (not stock)")
    suspend fun updateProduct(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateProductRequest
    ): ResponseEntity<ProductResponse> {
        val product = updateProductUseCase.execute(request.toCommand(id))
        return ResponseEntity.ok(product.toResponse())
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete product")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun deleteProduct(@PathVariable id: String) {
        deleteProductUseCase.execute(id)
    }

    @GetMapping
    @Operation(summary = "List products with pagination")
    suspend fun listProducts(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) minStock: Int?
    ): ResponseEntity<PagedResponse<ProductResponse>> {
        val result = listProductsUseCase.execute(ListProductsQuery(page, size, minStock))
        return ResponseEntity.ok(result.toResponse { it.toResponse() })
    }

    @PostMapping("/{id}/stock/reserve")
    @Operation(
        summary = "Reserve stock (with distributed lock)",
        description = "Atomically reserves stock using distributed locking for high-concurrency scenarios"
    )
    suspend fun reserveStock(
        @PathVariable id: String,
        @Valid @RequestBody request: StockOperationRequest
    ): ResponseEntity<ProductResponse> {
        val product = reserveStockUseCase.execute(request.toReserveCommand(id))
        return ResponseEntity.ok(product.toResponse())
    }

    @PostMapping("/{id}/stock/restore")
    @Operation(
        summary = "Restore stock (with distributed lock)",
        description = "Atomically restores stock using distributed locking"
    )
    suspend fun restoreStock(
        @PathVariable id: String,
        @Valid @RequestBody request: StockOperationRequest
    ): ResponseEntity<ProductResponse> {
        val product = restoreStockUseCase.execute(request.toRestoreCommand(id))
        return ResponseEntity.ok(product.toResponse())
    }

    @PutMapping("/{id}/stock")
    @Operation(
        summary = "Update stock (with optimistic locking)",
        description = "Updates stock using optimistic locking with retry for moderate contention"
    )
    suspend fun updateStock(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateStockRequest
    ): ResponseEntity<ProductResponse> {
        val product = updateStockWithLockUseCase.execute(request.toCommand(id))
        return ResponseEntity.ok(product.toResponse())
    }
}
