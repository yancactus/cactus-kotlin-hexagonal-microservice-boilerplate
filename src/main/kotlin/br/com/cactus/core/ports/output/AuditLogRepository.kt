package br.com.cactus.core.ports.output

import br.com.cactus.core.domain.AuditLog
import br.com.cactus.core.domain.EntityType
import java.time.Instant

interface AuditLogRepository {

    suspend fun save(auditLog: AuditLog): AuditLog

    suspend fun findByEntityTypeAndTimeRange(
        entityType: EntityType,
        startTime: Instant,
        endTime: Instant,
        limit: Int = 100
    ): List<AuditLog>

    suspend fun findByEntityId(entityId: String, limit: Int = 100): List<AuditLog>

    suspend fun findById(id: String): AuditLog?
}
