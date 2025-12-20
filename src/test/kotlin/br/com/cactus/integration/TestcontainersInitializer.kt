package br.com.cactus.integration

import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.support.TestPropertySourceUtils
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName

class TestcontainersInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {

    companion object {
        val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7"))
            .withExposedPorts(6379)
            .apply { start() }

        val localstack: LocalStackContainer = LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(LocalStackContainer.Service.DYNAMODB)
            .apply { start() }
    }

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
            applicationContext,
            "spring.data.redis.host=${redis.host}",
            "spring.data.redis.port=${redis.getMappedPort(6379)}",
            "aws.dynamodb.endpoint=${localstack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB)}",
            "aws.dynamodb.region=${localstack.region}",
            "aws.dynamodb.access-key=${localstack.accessKey}",
            "aws.dynamodb.secret-key=${localstack.secretKey}",
            "aws.dynamodb.create-tables=true"
        )
    }
}
