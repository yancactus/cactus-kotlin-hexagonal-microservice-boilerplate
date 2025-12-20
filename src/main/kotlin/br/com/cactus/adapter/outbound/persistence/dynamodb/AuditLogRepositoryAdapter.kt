package br.com.cactus.adapter.outbound.persistence.dynamodb

import br.com.cactus.adapter.outbound.persistence.dynamodb.entity.AuditLogItem
import br.com.cactus.adapter.outbound.persistence.dynamodb.mapper.toDomain
import br.com.cactus.adapter.outbound.persistence.dynamodb.mapper.toItem
import br.com.cactus.core.domain.AuditLog
import br.com.cactus.core.domain.EntityType
import br.com.cactus.core.ports.output.AuditLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest
import java.time.Instant

@Component
class AuditLogRepositoryAdapter(
    private val auditLogTable: DynamoDbTable<AuditLogItem>
) : AuditLogRepository {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun save(auditLog: AuditLog): AuditLog = withContext(Dispatchers.IO) {
        logger.debug("Saving audit log: entityType=${auditLog.entityType}, entityId=${auditLog.entityId}, action=${auditLog.action}")

        val item = auditLog.toItem()
        auditLogTable.putItem(item)

        logger.debug("Audit log saved successfully: id=${auditLog.id}")
        auditLog
    }

    override suspend fun findByEntityTypeAndTimeRange(
        entityType: EntityType,
        startTime: Instant,
        endTime: Instant,
        limit: Int
    ): List<AuditLog> = withContext(Dispatchers.IO) {
        logger.debug("Finding audit logs: entityType=$entityType, startTime=$startTime, endTime=$endTime")

        val queryConditional = QueryConditional.sortBetween(
            Key.builder().partitionValue(entityType.name).sortValue("$startTime").build(),
            Key.builder().partitionValue(entityType.name).sortValue("$endTime~").build()
        )

        val request = QueryEnhancedRequest.builder()
            .queryConditional(queryConditional)
            .limit(limit)
            .build()

        auditLogTable.query(request)
            .items()
            .toList()
            .map { it.toDomain() }
    }

    override suspend fun findByEntityId(entityId: String, limit: Int): List<AuditLog> = withContext(Dispatchers.IO) {
        logger.debug("Finding audit logs by entityId: $entityId")

        val index = auditLogTable.index(AuditLogItem.ENTITY_ID_INDEX)

        val queryConditional = QueryConditional.keyEqualTo(
            Key.builder().partitionValue(entityId).build()
        )

        val request = QueryEnhancedRequest.builder()
            .queryConditional(queryConditional)
            .limit(limit)
            .build()

        index.query(request)
            .flatMap { it.items() }
            .toList()
            .map { it.toDomain() }
            .sortedByDescending { it.timestamp }
    }

    override suspend fun findById(id: String): AuditLog? = withContext(Dispatchers.IO) {
        logger.debug("Finding audit log by id: $id")

        // Scan all partitions to find by id (not optimal, but works for edge cases)
        EntityType.entries.forEach { entityType ->
            val queryConditional = QueryConditional.sortBeginsWith(
                Key.builder()
                    .partitionValue(entityType.name)
                    .sortValue("")
                    .build()
            )

            val request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .build()

            val found = auditLogTable.query(request)
                .items()
                .toList()
                .find { it.id == id }

            if (found != null) {
                return@withContext found.toDomain()
            }
        }

        null
    }
}
