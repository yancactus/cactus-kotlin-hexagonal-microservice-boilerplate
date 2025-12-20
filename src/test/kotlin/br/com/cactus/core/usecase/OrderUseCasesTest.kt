package br.com.cactus.core.usecase

import br.com.cactus.core.domain.Order
import br.com.cactus.core.domain.OrderItem
import br.com.cactus.core.domain.OrderStatus
import br.com.cactus.core.domain.Product
import br.com.cactus.core.domain.User
import br.com.cactus.core.exception.EntityNotFoundException
import br.com.cactus.core.ports.input.CreateOrderCommand
import br.com.cactus.core.ports.input.OrderItemCommand
import br.com.cactus.core.ports.output.EventPublisher
import br.com.cactus.core.ports.output.MultiLockHandle
import br.com.cactus.core.ports.output.MultiLockPort
import br.com.cactus.core.ports.output.OrderRepository
import br.com.cactus.core.ports.output.ProductRepository
import br.com.cactus.core.ports.output.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.UUID
import kotlin.time.Duration

class OrderUseCasesTest {

    private lateinit var orderRepository: OrderRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var userRepository: UserRepository
    private lateinit var eventPublisher: EventPublisher
    private lateinit var multiLockPort: MultiLockPort

    /** Fake MultiLockPort that executes the block directly without locking */
    private class FakeMultiLockPort : MultiLockPort {
        override suspend fun tryLockAll(
            resourceIds: List<String>,
            waitTime: Duration,
            leaseTime: Duration
        ): MultiLockHandle? = object : MultiLockHandle {
            override val resourceIds: List<String> = resourceIds
            override suspend fun unlockAll() {}
            override fun close() {}
        }

        override suspend fun <T> withLocks(
            resourceIds: List<String>,
            waitTime: Duration,
            leaseTime: Duration,
            block: suspend () -> T
        ): T = block()
    }

    @BeforeEach
    fun setup() {
        orderRepository = mockk()
        productRepository = mockk()
        userRepository = mockk()
        eventPublisher = mockk()
        multiLockPort = FakeMultiLockPort()
    }

    @Nested
    inner class CreateOrderUseCaseTest {
        private lateinit var useCase: CreateOrderUseCaseImpl

        @BeforeEach
        fun setup() {
            useCase = CreateOrderUseCaseImpl(orderRepository, productRepository, userRepository, eventPublisher)
        }

        @Test
        fun `should create order successfully`() = runTest {
            val userId = UUID.randomUUID()
            val productId = "product-123"

            val user = User(id = userId, name = "John", email = "john@example.com")
            val product = Product(
                id = productId,
                sku = "SKU-001",
                name = "Test Product",
                description = "Description",
                price = BigDecimal("99.99"),
                stock = 100
            )

            val command = CreateOrderCommand(
                userId = userId,
                items = listOf(OrderItemCommand(productId = productId, quantity = 2))
            )

            val savedOrder = Order(
                id = UUID.randomUUID(),
                userId = userId,
                items = listOf(
                    OrderItem(
                        productId = productId,
                        productName = product.name,
                        quantity = 2,
                        unitPrice = product.price
                    )
                )
            )

            coEvery { userRepository.findById(userId) } returns user
            coEvery { productRepository.findByIds(listOf(productId)) } returns listOf(product)
            coEvery { orderRepository.save(any()) } returns savedOrder
            coEvery { eventPublisher.publish(any()) } just runs

            val result = useCase.execute(command)

            assertNotNull(result.id)
            assertEquals(userId, result.userId)
            assertEquals(1, result.items.size)
            assertEquals(OrderStatus.PENDING, result.status)
        }

        @Test
        fun `should throw EntityNotFoundException when user not found`() = runTest {
            val userId = UUID.randomUUID()
            val command = CreateOrderCommand(
                userId = userId,
                items = listOf(OrderItemCommand(productId = "product-123", quantity = 1))
            )

            coEvery { userRepository.findById(userId) } returns null

            assertThrows<EntityNotFoundException> {
                useCase.execute(command)
            }
        }

        @Test
        fun `should throw EntityNotFoundException when product not found`() = runTest {
            val userId = UUID.randomUUID()
            val user = User(id = userId, name = "John", email = "john@example.com")

            val command = CreateOrderCommand(
                userId = userId,
                items = listOf(OrderItemCommand(productId = "non-existent", quantity = 1))
            )

            coEvery { userRepository.findById(userId) } returns user
            coEvery { productRepository.findByIds(listOf("non-existent")) } returns emptyList()

            assertThrows<EntityNotFoundException> {
                useCase.execute(command)
            }
        }
    }

    @Nested
    inner class GetOrderUseCaseTest {
        private lateinit var useCase: GetOrderUseCaseImpl

        @BeforeEach
        fun setup() {
            useCase = GetOrderUseCaseImpl(orderRepository)
        }

        @Test
        fun `should return order when found`() = runTest {
            val orderId = UUID.randomUUID()
            val order = Order(
                id = orderId,
                userId = UUID.randomUUID(),
                items = listOf(
                    OrderItem(
                        productId = "product-123",
                        productName = "Test Product",
                        quantity = 1,
                        unitPrice = BigDecimal("10.00")
                    )
                )
            )

            coEvery { orderRepository.findById(orderId) } returns order

            val result = useCase.execute(orderId)

            assertEquals(orderId, result.id)
        }

        @Test
        fun `should throw EntityNotFoundException when order not found`() = runTest {
            val orderId = UUID.randomUUID()

            coEvery { orderRepository.findById(orderId) } returns null

            assertThrows<EntityNotFoundException> {
                useCase.execute(orderId)
            }
        }
    }

    @Nested
    inner class ConfirmOrderUseCaseTest {
        private lateinit var useCase: ConfirmOrderUseCaseImpl

        @BeforeEach
        fun setup() {
            useCase = ConfirmOrderUseCaseImpl(orderRepository, productRepository, multiLockPort, eventPublisher)
        }

        @Test
        fun `should confirm order and reserve stock`() = runTest {
            val orderId = UUID.randomUUID()
            val productId = "product-123"

            val order = Order(
                id = orderId,
                userId = UUID.randomUUID(),
                items = listOf(
                    OrderItem(
                        productId = productId,
                        productName = "Test",
                        quantity = 5,
                        unitPrice = BigDecimal("10.00")
                    )
                ),
                status = OrderStatus.PENDING
            )

            val product = Product(
                id = productId,
                sku = "SKU-001",
                name = "Test",
                description = "Description",
                price = BigDecimal("10.00"),
                stock = 100
            )

            val confirmedOrder = order.copy(status = OrderStatus.CONFIRMED)

            coEvery { orderRepository.findById(orderId) } returns order
            coEvery { productRepository.findById(productId) } returns product
            coEvery { productRepository.save(any()) } returns product.copy(stock = 95)
            coEvery { orderRepository.save(any()) } returns confirmedOrder
            coEvery { eventPublisher.publish(any()) } just runs

            val result = useCase.execute(orderId)

            assertEquals(OrderStatus.CONFIRMED, result.status)
        }

        @Test
        fun `should throw EntityNotFoundException when order not found`() = runTest {
            val orderId = UUID.randomUUID()

            coEvery { orderRepository.findById(orderId) } returns null

            assertThrows<EntityNotFoundException> {
                useCase.execute(orderId)
            }
        }
    }

    @Nested
    inner class CancelOrderUseCaseTest {
        private lateinit var useCase: CancelOrderUseCaseImpl

        @BeforeEach
        fun setup() {
            useCase = CancelOrderUseCaseImpl(orderRepository, productRepository, multiLockPort, eventPublisher)
        }

        @Test
        fun `should cancel pending order without restoring stock`() = runTest {
            val orderId = UUID.randomUUID()
            val productId = "product-123"

            val order = Order(
                id = orderId,
                userId = UUID.randomUUID(),
                items = listOf(
                    OrderItem(
                        productId = productId,
                        productName = "Test",
                        quantity = 5,
                        unitPrice = BigDecimal("10.00")
                    )
                ),
                status = OrderStatus.PENDING
            )

            val cancelledOrder = order.copy(status = OrderStatus.CANCELLED)

            coEvery { orderRepository.findById(orderId) } returns order
            coEvery { orderRepository.save(any()) } returns cancelledOrder
            coEvery { eventPublisher.publish(any()) } just runs

            val result = useCase.execute(orderId)

            assertEquals(OrderStatus.CANCELLED, result.status)
            coVerify(exactly = 0) { productRepository.save(any()) }
        }

        @Test
        fun `should cancel confirmed order and restore stock`() = runTest {
            val orderId = UUID.randomUUID()
            val productId = "product-123"

            val order = Order(
                id = orderId,
                userId = UUID.randomUUID(),
                items = listOf(
                    OrderItem(
                        productId = productId,
                        productName = "Test",
                        quantity = 5,
                        unitPrice = BigDecimal("10.00")
                    )
                ),
                status = OrderStatus.CONFIRMED
            )

            val product = Product(
                id = productId,
                sku = "SKU-001",
                name = "Test",
                description = "Description",
                price = BigDecimal("10.00"),
                stock = 95
            )

            val cancelledOrder = order.copy(status = OrderStatus.CANCELLED)

            coEvery { orderRepository.findById(orderId) } returns order
            coEvery { productRepository.findById(productId) } returns product
            coEvery { productRepository.save(any()) } returns product.copy(stock = 100)
            coEvery { orderRepository.save(any()) } returns cancelledOrder
            coEvery { eventPublisher.publish(any()) } just runs

            val result = useCase.execute(orderId)

            assertEquals(OrderStatus.CANCELLED, result.status)
            coVerify { productRepository.save(any()) }
        }
    }

    @Nested
    inner class CompleteOrderUseCaseTest {
        private lateinit var useCase: CompleteOrderUseCaseImpl

        @BeforeEach
        fun setup() {
            useCase = CompleteOrderUseCaseImpl(orderRepository, eventPublisher)
        }

        @Test
        fun `should complete confirmed order`() = runTest {
            val orderId = UUID.randomUUID()

            val order = Order(
                id = orderId,
                userId = UUID.randomUUID(),
                items = listOf(
                    OrderItem(
                        productId = "product-123",
                        productName = "Test Product",
                        quantity = 1,
                        unitPrice = BigDecimal("10.00")
                    )
                ),
                status = OrderStatus.CONFIRMED
            )

            val completedOrder = order.copy(status = OrderStatus.COMPLETED)

            coEvery { orderRepository.findById(orderId) } returns order
            coEvery { orderRepository.save(any()) } returns completedOrder
            coEvery { eventPublisher.publish(any()) } just runs

            val result = useCase.execute(orderId)

            assertEquals(OrderStatus.COMPLETED, result.status)
        }

        @Test
        fun `should throw EntityNotFoundException when order not found`() = runTest {
            val orderId = UUID.randomUUID()

            coEvery { orderRepository.findById(orderId) } returns null

            assertThrows<EntityNotFoundException> {
                useCase.execute(orderId)
            }
        }
    }
}
