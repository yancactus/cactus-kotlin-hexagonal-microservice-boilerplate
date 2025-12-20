package br.com.cactus.adapter.inbound.rest.controller

import br.com.cactus.core.domain.AuditLog
import br.com.cactus.core.domain.EntityType
import br.com.cactus.core.ports.output.AuditLogRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit Logs", description = "Endpoints for querying audit logs")
class AuditLogController(
    private val auditLogRepository: AuditLogRepository
) {

    @GetMapping("/entity-type/{entityType}")
    @Operation(summary = "Find audit logs by entity type and time range")
    suspend fun findByEntityType(
        @PathVariable entityType: EntityType,
        @Parameter(description = "Start time (ISO format)", example = "2024-01-01T00:00:00")
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startTime: LocalDateTime,
        @Parameter(description = "End time (ISO format)", example = "2024-12-31T23:59:59")
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endTime: LocalDateTime,
        @RequestParam(defaultValue = "100") limit: Int
    ): ResponseEntity<List<AuditLogResponse>> {
        val logs = auditLogRepository.findByEntityTypeAndTimeRange(
            entityType = entityType,
            startTime = startTime.toInstant(ZoneOffset.UTC),
            endTime = endTime.toInstant(ZoneOffset.UTC),
            limit = limit
        )
        return ResponseEntity.ok(logs.map { it.toResponse() })
    }

    @GetMapping("/entity/{entityId}")
    @Operation(summary = "Find audit logs by entity ID")
    suspend fun findByEntityId(
        @PathVariable entityId: String,
        @RequestParam(defaultValue = "100") limit: Int
    ): ResponseEntity<List<AuditLogResponse>> {
        val logs = auditLogRepository.findByEntityId(entityId, limit)
        return ResponseEntity.ok(logs.map { it.toResponse() })
    }

    @GetMapping("/{id}")
    @Operation(summary = "Find audit log by ID")
    suspend fun findById(@PathVariable id: String): ResponseEntity<AuditLogResponse> {
        val log = auditLogRepository.findById(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(log.toResponse())
    }
}

data class AuditLogResponse(
    val id: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val userId: String?,
    val oldValue: String?,
    val newValue: String?,
    val timestamp: Instant,
    val metadata: Map<String, String>
)

private fun AuditLog.toResponse() = AuditLogResponse(
    id = id,
    entityType = entityType.name,
    entityId = entityId,
    action = action.name,
    userId = userId,
    oldValue = oldValue,
    newValue = newValue,
    timestamp = timestamp,
    metadata = metadata
)
