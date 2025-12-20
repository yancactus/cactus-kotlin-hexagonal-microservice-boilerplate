package br.com.cactus.core.usecase

import br.com.cactus.core.config.CacheKeys
import br.com.cactus.core.config.CacheTtl
import br.com.cactus.core.config.LockConfig
import br.com.cactus.core.config.OptimisticLockConfig
import br.com.cactus.core.domain.Product
import br.com.cactus.core.domain.ProductCreatedEvent
import br.com.cactus.core.domain.ProductStockUpdatedEvent
import br.com.cactus.core.exception.ConcurrencyException
import br.com.cactus.core.exception.DuplicateEntityException
import br.com.cactus.core.exception.EntityNotFoundException
import br.com.cactus.core.ports.input.*
import br.com.cactus.core.ports.output.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Service
class CreateProductUseCaseImpl(
    private val productRepository: ProductRepository,
    private val cachePort: CachePort,
    private val eventPublisher: EventPublisher
) : CreateProductUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun execute(command: CreateProductCommand): Product = coroutineScope {
        logger.info("Creating product with SKU: ${command.sku}")

        if (productRepository.existsBySku(command.sku)) {
            throw DuplicateEntityException("Product", "sku", command.sku)
        }

        val product = Product(
            sku = command.sku,
            name = command.name,
            description = command.description,
            price = command.price,
            stock = command.initialStock
        )

        val savedProduct = productRepository.save(product)

        launch {
            cachePort.set(CacheKeys.product(savedProduct.id), savedProduct, CacheTtl.PRODUCT)
        }

        launch {
            eventPublisher.publish(
                ProductCreatedEvent(
                    productId = savedProduct.id,
                    sku = savedProduct.sku,
                    name = savedProduct.name,
                    price = savedProduct.price,
                    stock = savedProduct.stock
                )
            )
        }

        logger.info("Product created: ${savedProduct.id}")
        savedProduct
    }
}

@Service
class GetProductUseCaseImpl(
    private val productRepository: ProductRepository,
    private val cachePort: CachePort
) : GetProductUseCase {

    override suspend fun execute(id: String): Product {
        return cachePort.getOrPut(CacheKeys.product(id), Product::class.java, CacheTtl.PRODUCT) {
            productRepository.findById(id)
                ?: throw EntityNotFoundException("Product", id)
        }
    }
}

@Service
class UpdateProductUseCaseImpl(
    private val productRepository: ProductRepository,
    private val cachePort: CachePort
) : UpdateProductUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun execute(command: UpdateProductCommand): Product = coroutineScope {
        logger.info("Updating product: ${command.id}")

        val product = productRepository.findById(command.id)
            ?: throw EntityNotFoundException("Product", command.id)

        var updated = product
        command.name?.let { updated = updated.copy(name = it) }
        command.description?.let { updated = updated.copy(description = it) }
        command.price?.let { updated = updated.updatePrice(it) }

        val savedProduct = productRepository.save(updated)

        launch {
            cachePort.set(CacheKeys.product(savedProduct.id), savedProduct, CacheTtl.PRODUCT)
        }

        savedProduct
    }
}

@Service
class DeleteProductUseCaseImpl(
    private val productRepository: ProductRepository,
    private val cachePort: CachePort
) : DeleteProductUseCase {

    override suspend fun execute(id: String) {
        productRepository.delete(id)
        cachePort.delete(CacheKeys.product(id))
    }
}

@Service
class ListProductsUseCaseImpl(
    private val productRepository: ProductRepository
) : ListProductsUseCase {

    override suspend fun execute(query: ListProductsQuery): PagedResult<Product> {
        val pagedData = productRepository.findAll(query.page, query.size, query.minStock)

        return PagedResult(
            content = pagedData.content,
            page = query.page,
            size = query.size,
            totalElements = pagedData.totalElements,
            totalPages = pagedData.totalPages
        )
    }
}

@Service
class ReserveStockUseCaseImpl(
    private val productRepository: ProductRepository,
    private val distributedLockPort: DistributedLockPort,
    private val eventPublisher: EventPublisher,
    private val cachePort: CachePort
) : ReserveStockUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun execute(command: ReserveStockCommand): Product = coroutineScope {
        logger.info("Reserving stock for product ${command.productId}: ${command.quantity} units")

        distributedLockPort.withLock(
            resourceId = CacheKeys.productStock(command.productId),
            waitTime = LockConfig.STOCK_OPERATION_WAIT_TIME,
            leaseTime = LockConfig.STOCK_OPERATION_LEASE_TIME
        ) {
            val product = productRepository.findById(command.productId)
                ?: throw EntityNotFoundException("Product", command.productId)

            val previousStock = product.stock
            val updatedProduct = product.reserveStock(command.quantity)
            val savedProduct = productRepository.save(updatedProduct)

            launch {
                cachePort.set(CacheKeys.product(savedProduct.id), savedProduct, CacheTtl.PRODUCT)
            }

            launch {
                eventPublisher.publish(
                    ProductStockUpdatedEvent(
                        productId = savedProduct.id,
                        previousStock = previousStock,
                        newStock = savedProduct.stock,
                        reason = command.reason
                    )
                )
            }

            logger.info("Stock reserved: ${command.productId}, ${previousStock} -> ${savedProduct.stock}")
            savedProduct
        }
    }
}

@Service
class RestoreStockUseCaseImpl(
    private val productRepository: ProductRepository,
    private val distributedLockPort: DistributedLockPort,
    private val eventPublisher: EventPublisher,
    private val cachePort: CachePort
) : RestoreStockUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun execute(command: RestoreStockCommand): Product = coroutineScope {
        logger.info("Restoring stock for product ${command.productId}: ${command.quantity} units")

        distributedLockPort.withLock(
            resourceId = CacheKeys.productStock(command.productId),
            waitTime = LockConfig.STOCK_OPERATION_WAIT_TIME,
            leaseTime = LockConfig.STOCK_OPERATION_LEASE_TIME
        ) {
            val product = productRepository.findById(command.productId)
                ?: throw EntityNotFoundException("Product", command.productId)

            val previousStock = product.stock
            val updatedProduct = product.restoreStock(command.quantity)
            val savedProduct = productRepository.save(updatedProduct)

            launch {
                cachePort.set(CacheKeys.product(savedProduct.id), savedProduct, CacheTtl.PRODUCT)
            }

            launch {
                eventPublisher.publish(
                    ProductStockUpdatedEvent(
                        productId = savedProduct.id,
                        previousStock = previousStock,
                        newStock = savedProduct.stock,
                        reason = command.reason
                    )
                )
            }

            savedProduct
        }
    }
}

@Service
class UpdateStockWithLockUseCaseImpl(
    private val productRepository: ProductRepository,
    private val eventPublisher: EventPublisher,
    private val cachePort: CachePort
) : UpdateStockWithLockUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun execute(command: UpdateStockCommand): Product = coroutineScope {
        logger.info("Updating stock for product ${command.productId} to ${command.newStock}")

        var attempts = 0
        var result: Product? = null
        var currentBackoff = OptimisticLockConfig.INITIAL_BACKOFF

        while (attempts < OptimisticLockConfig.MAX_RETRIES && result == null) {
            val product = productRepository.findById(command.productId)
                ?: throw EntityNotFoundException("Product", command.productId)

            val previousStock = product.stock

            result = productRepository.updateStock(
                id = command.productId,
                expectedVersion = product.version,
                newStock = command.newStock
            )

            if (result != null) {
                launch {
                    cachePort.set(CacheKeys.product(result!!.id), result!!, CacheTtl.PRODUCT)
                }

                launch {
                    eventPublisher.publish(
                        ProductStockUpdatedEvent(
                            productId = result!!.id,
                            previousStock = previousStock,
                            newStock = result!!.stock,
                            reason = command.reason
                        )
                    )
                }
            } else {
                attempts++
                logger.warn(
                    "Optimistic lock conflict for product ${command.productId}, " +
                        "attempt $attempts/${OptimisticLockConfig.MAX_RETRIES}, " +
                        "backing off for ${currentBackoff.inWholeMilliseconds}ms"
                )

                if (attempts < OptimisticLockConfig.MAX_RETRIES) {
                    delay(currentBackoff)
                    currentBackoff = calculateNextBackoff(currentBackoff)
                }
            }
        }

        result ?: throw ConcurrencyException("Product", command.productId)
    }

    private fun calculateNextBackoff(current: Duration): Duration {
        val nextMs = (current.inWholeMilliseconds * OptimisticLockConfig.BACKOFF_MULTIPLIER).toLong()
        return min(nextMs, OptimisticLockConfig.MAX_BACKOFF.inWholeMilliseconds).milliseconds
    }
}
