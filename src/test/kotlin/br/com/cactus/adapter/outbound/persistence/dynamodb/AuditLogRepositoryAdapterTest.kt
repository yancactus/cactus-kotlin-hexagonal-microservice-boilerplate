package br.com.cactus.adapter.outbound.persistence.dynamodb

import br.com.cactus.adapter.outbound.persistence.dynamodb.entity.AuditLogItem
import br.com.cactus.adapter.outbound.persistence.dynamodb.mapper.toItem
import br.com.cactus.core.domain.AuditAction
import br.com.cactus.core.domain.AuditLog
import br.com.cactus.core.domain.EntityType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.core.pagination.sync.SdkIterable
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.model.Page
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AuditLogRepositoryAdapterTest {

    private lateinit var auditLogTable: DynamoDbTable<AuditLogItem>
    private lateinit var adapter: AuditLogRepositoryAdapter

    @BeforeEach
    fun setUp() {
        auditLogTable = mockk(relaxed = true)
        adapter = AuditLogRepositoryAdapter(auditLogTable)
    }

    @Test
    fun `should save audit log`() = runBlocking {
        // Given
        val auditLog = AuditLog.create(
            entityType = EntityType.USER,
            entityId = "user-123",
            action = AuditAction.CREATE,
            userId = "admin",
            newValue = """{"name": "John"}"""
        )

        val itemSlot = slot<AuditLogItem>()
        every { auditLogTable.putItem(capture(itemSlot)) } returns Unit

        // When
        val result = adapter.save(auditLog)

        // Then
        assertNotNull(result)
        assertEquals(auditLog.id, result.id)
        assertEquals(auditLog.entityType, result.entityType)
        assertEquals(auditLog.entityId, result.entityId)
        assertEquals(auditLog.action, result.action)

        verify { auditLogTable.putItem(any<AuditLogItem>()) }

        val capturedItem = itemSlot.captured
        assertEquals(EntityType.USER.name, capturedItem.pk)
        assertEquals("user-123", capturedItem.entityId)
        assertEquals(AuditAction.CREATE.name, capturedItem.action)
    }

    @Test
    fun `should find by entity type and time range`() = runBlocking {
        // Given
        val now = Instant.now()
        val startTime = now.minusSeconds(3600)
        val endTime = now

        val item = AuditLog.create(
            entityType = EntityType.PRODUCT,
            entityId = "product-123",
            action = AuditAction.UPDATE
        ).toItem()

        val items = mutableListOf(item)
        val sdkIterable = SdkIterable<AuditLogItem> { items.iterator() as MutableIterator<AuditLogItem> }
        val pageIterable = mockk<PageIterable<AuditLogItem>>()

        every { pageIterable.items() } returns sdkIterable
        every { auditLogTable.query(any<QueryEnhancedRequest>()) } returns pageIterable

        // When
        val result = adapter.findByEntityTypeAndTimeRange(
            entityType = EntityType.PRODUCT,
            startTime = startTime,
            endTime = endTime,
            limit = 10
        )

        // Then
        assertEquals(1, result.size)
        assertEquals("product-123", result[0].entityId)
        assertEquals(EntityType.PRODUCT, result[0].entityType)
    }

    @Test
    fun `should find by entity id using GSI`() = runBlocking {
        // Given
        val entityId = "order-456"
        val item = AuditLog.create(
            entityType = EntityType.ORDER,
            entityId = entityId,
            action = AuditAction.CREATE
        ).toItem()

        val index = mockk<DynamoDbIndex<AuditLogItem>>()
        val page = mockk<Page<AuditLogItem>>()
        val pages = mutableListOf(page)
        val sdkIterable = SdkIterable<Page<AuditLogItem>> { pages.iterator() as MutableIterator<Page<AuditLogItem>> }

        every { auditLogTable.index(AuditLogItem.ENTITY_ID_INDEX) } returns index
        every { index.query(any<QueryEnhancedRequest>()) } returns sdkIterable
        every { page.items() } returns listOf(item)

        // When
        val result = adapter.findByEntityId(entityId, limit = 10)

        // Then
        assertEquals(1, result.size)
        assertEquals(entityId, result[0].entityId)
        assertEquals(EntityType.ORDER, result[0].entityType)
    }
}
