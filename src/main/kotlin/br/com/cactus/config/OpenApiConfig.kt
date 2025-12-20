package br.com.cactus.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig(
    @Value("\${spring.application.name:Cactus API}")
    private val applicationName: String
) {

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("$applicationName - Hexagonal Architecture Demo")
                    .version("1.0.0")
                    .description("""
                        A comprehensive Spring Boot application demonstrating:
                        - Hexagonal Architecture (Ports & Adapters)
                        - CRUD operations for Users, Products, and Orders
                        - Dual messaging with Kafka and RabbitMQ
                        - Caching with Redis
                        - Distributed Locking with Redisson
                        - Dual persistence with PostgreSQL (JPA) and MongoDB
                        - Optimistic and Pessimistic Locking patterns
                        - Kotlin Coroutines for async operations
                        - OpenTelemetry for distributed tracing
                    """.trimIndent())
                    .contact(
                        Contact()
                            .name("Cactus Team")
                            .email("team@cactus.com.br")
                    )
                    .license(
                        License()
                            .name("MIT License")
                            .url("https://opensource.org/licenses/MIT")
                    )
            )
            .servers(
                listOf(
                    Server().url("http://localhost:8080").description("Local Development"),
                    Server().url("http://localhost:8081").description("Docker Environment")
                )
            )
    }
}
