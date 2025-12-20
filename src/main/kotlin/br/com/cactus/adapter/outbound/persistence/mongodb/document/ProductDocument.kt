package br.com.cactus.adapter.outbound.persistence.mongodb.document

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.time.Instant

@Document(collection = "products")
data class ProductDocument(
    @Id
    val id: String,

    @Indexed(unique = true)
    val sku: String,

    val name: String,
    val description: String,
    val price: BigDecimal,
    val stock: Int,

    @Version
    val version: Long = 0,

    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
