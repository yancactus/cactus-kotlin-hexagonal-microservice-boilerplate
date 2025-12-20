package br.com.cactus.integration

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class IntegrationTestConfig {

    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:16"))
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test")

    @Bean
    @ServiceConnection
    fun mongodb(): MongoDBContainer = MongoDBContainer(DockerImageName.parse("mongo:7.0"))

    @Bean
    @ServiceConnection
    fun kafka(): KafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))

    @Bean
    @ServiceConnection
    fun rabbitmq(): RabbitMQContainer = RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"))
}
