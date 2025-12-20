package br.com.cactus.adapter.outbound.persistence.dynamodb.entity

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey

@DynamoDbBean
data class AuditLogItem(
    @get:DynamoDbPartitionKey
    @get:DynamoDbAttribute("pk")
    var pk: String = "",

    @get:DynamoDbSortKey
    @get:DynamoDbAttribute("sk")
    var sk: String = "",

    @get:DynamoDbAttribute("id")
    var id: String = "",

    @get:DynamoDbAttribute("entityType")
    var entityType: String = "",

    @get:DynamoDbSecondaryPartitionKey(indexNames = ["entityId-index"])
    @get:DynamoDbAttribute("entityId")
    var entityId: String = "",

    @get:DynamoDbAttribute("action")
    var action: String = "",

    @get:DynamoDbAttribute("userId")
    var userId: String? = null,

    @get:DynamoDbAttribute("oldValue")
    var oldValue: String? = null,

    @get:DynamoDbAttribute("newValue")
    var newValue: String? = null,

    @get:DynamoDbAttribute("timestamp")
    var timestamp: String = "",

    @get:DynamoDbAttribute("metadata")
    var metadata: Map<String, String> = emptyMap()
) {
    companion object {
        const val TABLE_NAME = "audit_logs"
        const val ENTITY_ID_INDEX = "entityId-index"
    }
}
