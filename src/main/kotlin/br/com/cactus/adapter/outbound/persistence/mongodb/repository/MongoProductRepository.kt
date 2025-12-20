package br.com.cactus.adapter.outbound.persistence.mongodb.repository

import br.com.cactus.adapter.outbound.persistence.mongodb.document.ProductDocument
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface MongoProductRepository : MongoRepository<ProductDocument, String> {
    fun findBySku(sku: String): ProductDocument?
    fun existsBySku(sku: String): Boolean
    fun findByIdIn(ids: List<String>): List<ProductDocument>

    @Query("{ 'stock': { \$gte: ?0 } }")
    fun findAllByMinStock(minStock: Int, pageable: Pageable): Page<ProductDocument>
}
