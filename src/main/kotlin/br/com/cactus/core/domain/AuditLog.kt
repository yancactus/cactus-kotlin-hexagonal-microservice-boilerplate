package br.com.cactus.core.domain

import java.time.Instant
import java.util.UUID

data class AuditLog(
    val id: String = UUID.randomUUID().toString(),
    val entityType: EntityType,
    val entityId: String,
    val action: AuditAction,
    val userId: String? = null,
    val oldValue: String? = null,
    val newValue: String? = null,
    val timestamp: Instant = Instant.now(),
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(entityId.isNotBlank()) { "Entity ID cannot be blank" }
    }

    companion object {
        fun create(
            entityType: EntityType,
            entityId: String,
            action: AuditAction,
            userId: String? = null,
            oldValue: String? = null,
            newValue: String? = null,
            metadata: Map<String, String> = emptyMap()
        ): AuditLog = AuditLog(
            entityType = entityType,
            entityId = entityId,
            action = action,
            userId = userId,
            oldValue = oldValue,
            newValue = newValue,
            metadata = metadata
        )
    }
}

enum class EntityType {
    USER,
    PRODUCT,
    ORDER,
    ADDRESS
}

enum class AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    READ
}
