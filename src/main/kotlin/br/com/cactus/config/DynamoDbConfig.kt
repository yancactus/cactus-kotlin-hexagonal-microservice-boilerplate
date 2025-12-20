package br.com.cactus.config

import br.com.cactus.adapter.outbound.persistence.dynamodb.entity.AuditLogItem
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition
import software.amazon.awssdk.services.dynamodb.model.BillingMode
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement
import software.amazon.awssdk.services.dynamodb.model.KeyType
import software.amazon.awssdk.services.dynamodb.model.Projection
import software.amazon.awssdk.services.dynamodb.model.ProjectionType
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType
import java.net.URI

@Configuration
class DynamoDbConfig(
    @Value("\${aws.dynamodb.endpoint:http://localhost:4566}")
    private val endpoint: String,

    @Value("\${aws.dynamodb.region:us-east-1}")
    private val region: String,

    @Value("\${aws.dynamodb.access-key:local}")
    private val accessKey: String,

    @Value("\${aws.dynamodb.secret-key:local}")
    private val secretKey: String
) {
    @Bean
    fun dynamoDbClient(): DynamoDbClient {
        return DynamoDbClient.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
                )
            )
            .build()
    }

    @Bean
    fun dynamoDbEnhancedClient(dynamoDbClient: DynamoDbClient): DynamoDbEnhancedClient {
        return DynamoDbEnhancedClient.builder()
            .dynamoDbClient(dynamoDbClient)
            .build()
    }

    @Bean
    fun auditLogTable(enhancedClient: DynamoDbEnhancedClient): DynamoDbTable<AuditLogItem> {
        return enhancedClient.table(
            AuditLogItem.TABLE_NAME,
            TableSchema.fromBean(AuditLogItem::class.java)
        )
    }
}

@Component
class DynamoDbTableInitializer(
    private val dynamoDbClient: DynamoDbClient,
    @Value("\${aws.dynamodb.create-tables:true}")
    private val createTables: Boolean
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments?) {
        if (createTables) {
            createAuditLogTableIfNotExists()
        }
    }

    private fun createAuditLogTableIfNotExists() {
        try {
            dynamoDbClient.describeTable { it.tableName(AuditLogItem.TABLE_NAME) }
            logger.info("Table '${AuditLogItem.TABLE_NAME}' already exists")
        } catch (e: ResourceNotFoundException) {
            logger.info("Creating table '${AuditLogItem.TABLE_NAME}'...")

            val createTableRequest = CreateTableRequest.builder()
                .tableName(AuditLogItem.TABLE_NAME)
                .keySchema(
                    KeySchemaElement.builder()
                        .attributeName("pk")
                        .keyType(KeyType.HASH)
                        .build(),
                    KeySchemaElement.builder()
                        .attributeName("sk")
                        .keyType(KeyType.RANGE)
                        .build()
                )
                .attributeDefinitions(
                    AttributeDefinition.builder()
                        .attributeName("pk")
                        .attributeType(ScalarAttributeType.S)
                        .build(),
                    AttributeDefinition.builder()
                        .attributeName("sk")
                        .attributeType(ScalarAttributeType.S)
                        .build(),
                    AttributeDefinition.builder()
                        .attributeName("entityId")
                        .attributeType(ScalarAttributeType.S)
                        .build()
                )
                .globalSecondaryIndexes(
                    GlobalSecondaryIndex.builder()
                        .indexName(AuditLogItem.ENTITY_ID_INDEX)
                        .keySchema(
                            KeySchemaElement.builder()
                                .attributeName("entityId")
                                .keyType(KeyType.HASH)
                                .build()
                        )
                        .projection(
                            Projection.builder()
                                .projectionType(ProjectionType.ALL)
                                .build()
                        )
                        .build()
                )
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build()

            dynamoDbClient.createTable(createTableRequest)
            logger.info("Table '${AuditLogItem.TABLE_NAME}' created successfully")
        } catch (e: Exception) {
            logger.warn("Failed to create DynamoDB table: ${e.message}. This is expected if DynamoDB is not available.")
        }
    }
}
