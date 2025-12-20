package br.com.cactus.adapter.outbound.persistence.mongodb

import br.com.cactus.adapter.outbound.persistence.mongodb.mapper.toDocument
import br.com.cactus.adapter.outbound.persistence.mongodb.mapper.toDomain
import br.com.cactus.adapter.outbound.persistence.mongodb.repository.MongoProductRepository
import br.com.cactus.core.domain.Product
import br.com.cactus.core.ports.output.PagedData
import br.com.cactus.core.ports.output.ProductRepository
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ProductRepositoryAdapter(
    private val mongoRepository: MongoProductRepository,
    private val mongoTemplate: MongoTemplate
) : ProductRepository {

    override suspend fun save(product: Product): Product {
        return mongoRepository.save(product.toDocument()).toDomain()
    }

    override suspend fun findById(id: String): Product? {
        return mongoRepository.findById(id).map { it.toDomain() }.orElse(null)
    }

    override suspend fun findBySku(sku: String): Product? {
        return mongoRepository.findBySku(sku)?.toDomain()
    }

    override suspend fun findAll(page: Int, size: Int, minStock: Int?): PagedData<Product> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val pageResult = if (minStock != null) {
            mongoRepository.findAllByMinStock(minStock, pageable)
        } else {
            mongoRepository.findAll(pageable)
        }

        return PagedData(
            content = pageResult.content.map { it.toDomain() },
            totalElements = pageResult.totalElements,
            totalPages = pageResult.totalPages
        )
    }

    override suspend fun findByIds(ids: List<String>): List<Product> {
        return mongoRepository.findByIdIn(ids).map { it.toDomain() }
    }

    override suspend fun existsBySku(sku: String): Boolean {
        return mongoRepository.existsBySku(sku)
    }

    override suspend fun delete(id: String) {
        mongoRepository.deleteById(id)
    }

    override suspend fun findByIdForUpdate(id: String): Product? {
        return findById(id)
    }

    override suspend fun updateStock(id: String, expectedVersion: Long, newStock: Int): Product? {
        return try {
            val query = Query.query(
                Criteria.where("_id").`is`(id)
                    .and("version").`is`(expectedVersion)
            )

            val update = Update()
                .set("stock", newStock)
                .set("updatedAt", Instant.now())
                .inc("version", 1)

            val result = mongoTemplate.findAndModify(
                query,
                update,
                org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true),
                br.com.cactus.adapter.outbound.persistence.mongodb.document.ProductDocument::class.java
            )

            result?.toDomain()
        } catch (e: OptimisticLockingFailureException) {
            null
        }
    }
}
