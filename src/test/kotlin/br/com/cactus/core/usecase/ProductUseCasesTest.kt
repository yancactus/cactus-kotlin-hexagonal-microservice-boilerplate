package br.com.cactus.core.usecase

import br.com.cactus.core.config.OptimisticLockConfig
import br.com.cactus.core.domain.Product
import br.com.cactus.core.exception.ConcurrencyException
import br.com.cactus.core.exception.DuplicateEntityException
import br.com.cactus.core.exception.EntityNotFoundException
import br.com.cactus.core.ports.input.CreateProductCommand
import br.com.cactus.core.ports.input.ReserveStockCommand
import br.com.cactus.core.ports.input.UpdateStockCommand
import br.com.cactus.core.ports.output.CachePort
import br.com.cactus.core.ports.output.DistributedLockPort
import br.com.cactus.core.ports.output.EventPublisher
import br.com.cactus.core.ports.output.LockHandle
import br.com.cactus.core.ports.output.ProductRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.time.Duration

class ProductUseCasesTest {

    private lateinit var productRepository: ProductRepository
    private lateinit var cachePort: CachePort
    private lateinit var distributedLockPort: DistributedLockPort
    private lateinit var eventPublisher: EventPublisher

    private class FakeDistributedLockPort : DistributedLockPort {
        override suspend fun tryLock(
            resourceId: String,
            waitTime: Duration,
            leaseTime: Duration
        ): LockHandle? = object : LockHandle {
            override val resourceId: String = resourceId
            override val isHeld: Boolean = true
            override suspend fun extend(additionalTime: Duration): Boolean = true
            override suspend fun unlock() {}
            override fun close() {}
        }

        override suspend fun lock(
            resourceId: String,
            waitTime: Duration,
            leaseTime: Duration
        ): LockHandle = tryLock(resourceId, waitTime, leaseTime)!!

        override suspend fun <T> withLock(
            resourceId: String,
            waitTime: Duration,
            leaseTime: Duration,
            block: suspend () -> T
        ): T = block()
    }

    @BeforeEach
    fun setup() {
        productRepository = mockk()
        cachePort = mockk()
        distributedLockPort = FakeDistributedLockPort()
        eventPublisher = mockk()
    }

    @Nested
    inner class CreateProductUseCaseTest {
        private lateinit var useCase: CreateProductUseCaseImpl

        @BeforeEach
        fun setup() {
            useCase = CreateProductUseCaseImpl(productRepository, cachePort, eventPublisher)
        }

        @Test
        fun `should create product successfully`() = runTest {
            val command = CreateProductCommand(
                sku = "SKU-001",
                name = "Test Product",
                description = "Description",
                price = BigDecimal("99.99"),
                initialStock = 100
            )

            val savedProduct = Product(
                id = "product-123",
                sku = command.sku,
                name = command.name,
                description = command.description,
                price = command.price,
                stock = command.initialStock
            )

            coEvery { productRepository.existsBySku(command.sku) } returns false
            coEvery { productRepository.save(any()) } returns savedProduct
            coEvery { cachePort.set(any(), any(), any()) } just runs
            coEvery { eventPublisher.publish(any()) } just runs

            val result = useCase.execute(command)

            assertNotNull(result.id)
            assertEquals(command.sku, result.sku)
            assertEquals(command.name, result.name)
            assertEquals(command.initialStock, result.stock)

            coVerify { productRepository.existsBySku(command.sku) }
            coVerify { productRepository.save(any()) }
        }

        @Test
        fun `should throw DuplicateEntityException when SKU exists`() = runTest {
            val command = CreateProductCommand(
                sku = "EXISTING-SKU",
                name = "Test Product",
                description = "Description",
                price = BigDecimal("99.99"),
                initialStock = 100
            )

            coEvery { productRepository.existsBySku(command.sku) } returns true

            assertThrows<DuplicateEntityException> {
                useCase.execute(command)
            }
        }
    }

    @Nested
    inner class GetProductUseCaseTest {
        private lateinit var useCase: GetProductUseCaseImpl

        @BeforeEach
        fun setup() {
            useCase = GetProductUseCaseImpl(productRepository, cachePort)
        }

        @Test
        fun `should return product from cache when available`() = runTest {
            val productId = "product-123"
            val cachedProduct = Product(
                id = productId,
                sku = "SKU-001",
                name = "Cached Product",
                description = "Description",
                price = BigDecimal("50.00"),
                stock = 10
            )

            coEvery { cachePort.getOrPut(any(), Product::class.java, any(), any()) } returns cachedProduct

            val result = useCase.execute(productId)

            assertEquals(cachedProduct.id, result.id)
            assertEquals(cachedProduct.name, result.name)
        }

        @Test
        fun `should throw EntityNotFoundException when product not found`() = runTest {
            val productId = "non-existent"
            val computeSlot = slot<suspend () -> Product>()

            coEvery { cachePort.getOrPut(any(), Product::class.java, any(), capture(computeSlot)) } coAnswers {
                computeSlot.captured.invoke()
            }
            coEvery { productRepository.findById(productId) } returns null

            assertThrows<EntityNotFoundException> {
                useCase.execute(productId)
            }
        }
    }

    @Nested
    inner class ReserveStockUseCaseTest {
        private lateinit var useCase: ReserveStockUseCaseImpl

        @BeforeEach
        fun setup() {
            useCase = ReserveStockUseCaseImpl(productRepository, distributedLockPort, eventPublisher, cachePort)
        }

        @Test
        fun `should reserve stock with distributed lock`() = runTest {
            val productId = "product-123"
            val command = ReserveStockCommand(
                productId = productId,
                quantity = 5,
                reason = "Order #123"
            )

            val product = Product(
                id = productId,
                sku = "SKU-001",
                name = "Test Product",
                description = "Description",
                price = BigDecimal("50.00"),
                stock = 100
            )

            val updatedProduct = product.copy(stock = 95)

            coEvery { productRepository.findById(productId) } returns product
            coEvery { productRepository.save(any()) } returns updatedProduct
            coEvery { cachePort.set(any(), any(), any()) } just runs
            coEvery { eventPublisher.publish(any()) } just runs

            val result = useCase.execute(command)

            assertEquals(95, result.stock)
        }
    }

    @Nested
    inner class UpdateStockWithLockUseCaseTest {
        private lateinit var useCase: UpdateStockWithLockUseCaseImpl

        @BeforeEach
        fun setup() {
            useCase = UpdateStockWithLockUseCaseImpl(productRepository, eventPublisher, cachePort)
        }

        @Test
        fun `should update stock on first attempt`() = runTest {
            val productId = "product-123"
            val command = UpdateStockCommand(
                productId = productId,
                newStock = 50,
                reason = "Stock adjustment"
            )

            val product = Product(
                id = productId,
                sku = "SKU-001",
                name = "Test Product",
                description = "Description",
                price = BigDecimal("50.00"),
                stock = 100,
                version = 1
            )

            val updatedProduct = product.copy(stock = 50, version = 2)

            coEvery { productRepository.findById(productId) } returns product
            coEvery { productRepository.updateStock(productId, 1, 50) } returns updatedProduct
            coEvery { cachePort.set(any(), any(), any()) } just runs
            coEvery { eventPublisher.publish(any()) } just runs

            val result = useCase.execute(command)

            assertEquals(50, result.stock)
            coVerify(exactly = 1) { productRepository.updateStock(any(), any(), any()) }
        }

        @Test
        fun `should retry with exponential backoff on optimistic lock failure`() = runTest {
            val productId = "product-123"
            val command = UpdateStockCommand(
                productId = productId,
                newStock = 50,
                reason = "Stock adjustment"
            )

            val product = Product(
                id = productId,
                sku = "SKU-001",
                name = "Test Product",
                description = "Description",
                price = BigDecimal("50.00"),
                stock = 100,
                version = 1
            )

            val updatedProduct = product.copy(stock = 50, version = 2)

            var attempts = 0
            coEvery { productRepository.findById(productId) } returns product
            coEvery { productRepository.updateStock(productId, any(), 50) } answers {
                attempts++
                if (attempts < 3) null else updatedProduct
            }
            coEvery { cachePort.set(any(), any(), any()) } just runs
            coEvery { eventPublisher.publish(any()) } just runs

            val result = useCase.execute(command)

            assertEquals(50, result.stock)
            assertEquals(3, attempts)
        }

        @Test
        fun `should throw ConcurrencyException after max retries`() = runTest {
            val productId = "product-123"
            val command = UpdateStockCommand(
                productId = productId,
                newStock = 50,
                reason = "Stock adjustment"
            )

            val product = Product(
                id = productId,
                sku = "SKU-001",
                name = "Test Product",
                description = "Description",
                price = BigDecimal("50.00"),
                stock = 100,
                version = 1
            )

            coEvery { productRepository.findById(productId) } returns product
            coEvery { productRepository.updateStock(productId, any(), 50) } returns null

            assertThrows<ConcurrencyException> {
                useCase.execute(command)
            }

            coVerify(exactly = OptimisticLockConfig.MAX_RETRIES) {
                productRepository.updateStock(any(), any(), any())
            }
        }

        @Test
        fun `should throw EntityNotFoundException when product not found`() = runTest {
            val command = UpdateStockCommand(
                productId = "non-existent",
                newStock = 50,
                reason = "Stock adjustment"
            )

            coEvery { productRepository.findById("non-existent") } returns null

            assertThrows<EntityNotFoundException> {
                useCase.execute(command)
            }
        }
    }
}
