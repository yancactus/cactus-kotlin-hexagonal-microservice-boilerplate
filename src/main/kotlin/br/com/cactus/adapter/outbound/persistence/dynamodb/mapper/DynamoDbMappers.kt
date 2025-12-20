package br.com.cactus.adapter.outbound.persistence.dynamodb.mapper

import br.com.cactus.adapter.outbound.persistence.dynamodb.entity.AuditLogItem
import br.com.cactus.core.domain.AuditAction
import br.com.cactus.core.domain.AuditLog
import br.com.cactus.core.domain.EntityType
import java.time.Instant

fun AuditLog.toItem(): AuditLogItem = AuditLogItem(
    pk = entityType.name,
    sk = "${timestamp}#${id}",
    id = id,
    entityType = entityType.name,
    entityId = entityId,
    action = action.name,
    userId = userId,
    oldValue = oldValue,
    newValue = newValue,
    timestamp = timestamp.toString(),
    metadata = metadata
)

fun AuditLogItem.toDomain(): AuditLog = AuditLog(
    id = id,
    entityType = EntityType.valueOf(entityType),
    entityId = entityId,
    action = AuditAction.valueOf(action),
    userId = userId,
    oldValue = oldValue,
    newValue = newValue,
    timestamp = Instant.parse(timestamp),
    metadata = metadata
)
