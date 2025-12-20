package br.com.cactus.core.usecase

import br.com.cactus.core.config.CacheKeys
import br.com.cactus.core.config.LockConfig
import br.com.cactus.core.config.StockUpdateReason
import br.com.cactus.core.domain.*
import br.com.cactus.core.exception.EntityNotFoundException
import br.com.cactus.core.ports.input.*
import br.com.cactus.core.ports.output.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CreateOrderUseCaseImpl(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val eventPublisher: EventPublisher
) : CreateOrderUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun execute(command: CreateOrderCommand): Order = coroutineScope {
        logger.info("Creating order for user: ${command.userId}")

        // Verify user exists
        userRepository.findById(command.userId)
            ?: throw EntityNotFoundException("User", command.userId)

        // Fetch all products concurrently
        val productIds = command.items.map { it.productId }
        val products = productRepository.findByIds(productIds)
            .associateBy { it.id }

        // Validate all products exist
        val missingProducts = productIds.filter { it !in products.keys }
        if (missingProducts.isNotEmpty()) {
            throw EntityNotFoundException("Products", missingProducts.joinToString())
        }

        // Build order items
        val orderItems = command.items.map { item ->
            val product = products[item.productId]!!
            OrderItem(
                productId = product.id,
                productName = product.name,
                quantity = item.quantity,
                unitPrice = product.price
            )
        }

        val order = Order(
            userId = command.userId,
            items = orderItems
        )

        val savedOrder = orderRepository.save(order)

        // Publish event asynchronously
        launch {
            eventPublisher.publish(
                OrderCreatedEvent(
                    orderId = savedOrder.id,
                    userId = savedOrder.userId,
                    items = savedOrder.items.map { OrderItemEvent(it.productId, it.quantity) }
                )
            )
        }

        logger.info("Order created: ${savedOrder.id}, total: ${savedOrder.total}")
        savedOrder
    }
}

@Service
class GetOrderUseCaseImpl(
    private val orderRepository: OrderRepository
) : GetOrderUseCase {

    override suspend fun execute(id: UUID): Order {
        return orderRepository.findById(id)
            ?: throw EntityNotFoundException("Order", id)
    }
}

@Service
class ListOrdersUseCaseImpl(
    private val orderRepository: OrderRepository
) : ListOrdersUseCase {

    override suspend fun execute(query: ListOrdersQuery): PagedResult<Order> {
        val pagedData = orderRepository.findAll(query.page, query.size, query.status)
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
class ListUserOrdersUseCaseImpl(
    private val orderRepository: OrderRepository
) : ListUserOrdersUseCase {

    override suspend fun execute(userId: UUID, query: ListOrdersQuery): PagedResult<Order> {
        val pagedData = orderRepository.findByUserId(userId, query.page, query.size, query.status)
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
class ConfirmOrderUseCaseImpl(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val multiLockPort: MultiLockPort,
    private val eventPublisher: EventPublisher
) : ConfirmOrderUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun execute(orderId: UUID): Order = coroutineScope {
        logger.info("Confirming order: $orderId")

        val order = orderRepository.findById(orderId)
            ?: throw EntityNotFoundException("Order", orderId)

        val lockKeys = order.items.map { CacheKeys.productStock(it.productId) }

        multiLockPort.withLocks(
            resourceIds = lockKeys,
            waitTime = LockConfig.ORDER_OPERATION_WAIT_TIME,
            leaseTime = LockConfig.ORDER_OPERATION_LEASE_TIME
        ) {
            val lockedOrder = orderRepository.findById(orderId)
                ?: throw EntityNotFoundException("Order", orderId)

            val confirmedOrder = lockedOrder.confirm()

            val stockReservations = order.items.map { item ->
                async {
                    val product = productRepository.findById(item.productId)!!
                    val updatedProduct = product.reserveStock(item.quantity)
                    productRepository.save(updatedProduct)

                    ProductStockUpdatedEvent(
                        productId = product.id,
                        previousStock = product.stock,
                        newStock = updatedProduct.stock,
                        reason = StockUpdateReason.ORDER_CONFIRMED.format(orderId)
                    )
                }
            }.awaitAll()

            val savedOrder = orderRepository.save(confirmedOrder)

            launch {
                eventPublisher.publish(
                    OrderStatusChangedEvent(
                        orderId = savedOrder.id,
                        previousStatus = OrderStatus.PENDING,
                        newStatus = OrderStatus.CONFIRMED
                    )
                )
                stockReservations.forEach { eventPublisher.publish(it) }
            }

            logger.info("Order confirmed: $orderId")
            savedOrder
        }
    }
}

@Service
class CancelOrderUseCaseImpl(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val multiLockPort: MultiLockPort,
    private val eventPublisher: EventPublisher
) : CancelOrderUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun execute(orderId: UUID): Order = coroutineScope {
        logger.info("Cancelling order: $orderId")

        val order = orderRepository.findById(orderId)
            ?: throw EntityNotFoundException("Order", orderId)

        val wasConfirmed = order.status == OrderStatus.CONFIRMED
        val lockKeys = order.items.map { CacheKeys.productStock(it.productId) }

        multiLockPort.withLocks(
            resourceIds = lockKeys,
            waitTime = LockConfig.ORDER_OPERATION_WAIT_TIME,
            leaseTime = LockConfig.ORDER_OPERATION_LEASE_TIME
        ) {
            val lockedOrder = orderRepository.findById(orderId)
                ?: throw EntityNotFoundException("Order", orderId)

            val previousStatus = lockedOrder.status
            val cancelledOrder = lockedOrder.cancel()

            val stockRestorations = if (wasConfirmed) {
                order.items.map { item ->
                    async {
                        val product = productRepository.findById(item.productId)!!
                        val updatedProduct = product.restoreStock(item.quantity)
                        productRepository.save(updatedProduct)

                        ProductStockUpdatedEvent(
                            productId = product.id,
                            previousStock = product.stock,
                            newStock = updatedProduct.stock,
                            reason = StockUpdateReason.ORDER_CANCELLED.format(orderId)
                        )
                    }
                }.awaitAll()
            } else emptyList()

            val savedOrder = orderRepository.save(cancelledOrder)

            launch {
                eventPublisher.publish(
                    OrderStatusChangedEvent(
                        orderId = savedOrder.id,
                        previousStatus = previousStatus,
                        newStatus = OrderStatus.CANCELLED
                    )
                )
                stockRestorations.forEach { eventPublisher.publish(it) }
            }

            logger.info("Order cancelled: $orderId")
            savedOrder
        }
    }
}

@Service
class CompleteOrderUseCaseImpl(
    private val orderRepository: OrderRepository,
    private val eventPublisher: EventPublisher
) : CompleteOrderUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun execute(orderId: UUID): Order = coroutineScope {
        logger.info("Completing order: $orderId")

        val order = orderRepository.findById(orderId)
            ?: throw EntityNotFoundException("Order", orderId)

        val completedOrder = order.complete()
        val savedOrder = orderRepository.save(completedOrder)

        launch {
            eventPublisher.publish(
                OrderStatusChangedEvent(
                    orderId = savedOrder.id,
                    previousStatus = OrderStatus.CONFIRMED,
                    newStatus = OrderStatus.COMPLETED
                )
            )
        }

        logger.info("Order completed: $orderId")
        savedOrder
    }
}
