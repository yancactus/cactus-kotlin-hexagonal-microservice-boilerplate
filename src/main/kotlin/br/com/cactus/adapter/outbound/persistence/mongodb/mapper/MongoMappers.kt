package br.com.cactus.adapter.outbound.persistence.mongodb.mapper

import br.com.cactus.adapter.outbound.persistence.mongodb.document.ProductDocument
import br.com.cactus.core.domain.Product

fun ProductDocument.toDomain() = Product(
    id = id,
    sku = sku,
    name = name,
    description = description,
    price = price,
    stock = stock,
    version = version,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Product.toDocument() = ProductDocument(
    id = id,
    sku = sku,
    name = name,
    description = description,
    price = price,
    stock = stock,
    version = version,
    createdAt = createdAt,
    updatedAt = updatedAt
)
