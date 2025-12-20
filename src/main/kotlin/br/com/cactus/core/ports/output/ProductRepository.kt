package br.com.cactus.core.ports.output

import br.com.cactus.core.domain.Product

interface ProductRepository {
    suspend fun save(product: Product): Product
    suspend fun findById(id: String): Product?
    suspend fun findBySku(sku: String): Product?
    suspend fun findAll(page: Int, size: Int, minStock: Int?): PagedData<Product>
    suspend fun findByIds(ids: List<String>): List<Product>
    suspend fun existsBySku(sku: String): Boolean
    suspend fun delete(id: String)
    suspend fun findByIdForUpdate(id: String): Product?
    suspend fun updateStock(id: String, expectedVersion: Long, newStock: Int): Product?
}
