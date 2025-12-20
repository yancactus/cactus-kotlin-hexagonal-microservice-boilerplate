package br.com.cactus.config

import feign.Logger
import feign.Request
import feign.Retryer
import feign.codec.ErrorDecoder
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
@EnableFeignClients(basePackages = ["br.com.cactus.adapter.outbound.client"])
class FeignConfig(
    @Value("\${feign.connection-timeout:5000}")
    private val connectionTimeout: Long,

    @Value("\${feign.read-timeout:10000}")
    private val readTimeout: Long,

    @Value("\${feign.retry.max-attempts:3}")
    private val maxAttempts: Int,

    @Value("\${feign.retry.period:1000}")
    private val retryPeriod: Long,

    @Value("\${feign.retry.max-period:5000}")
    private val maxRetryPeriod: Long
) {

    @Bean
    fun requestOptions(): Request.Options {
        return Request.Options(
            connectionTimeout, TimeUnit.MILLISECONDS,
            readTimeout, TimeUnit.MILLISECONDS,
            true // followRedirects
        )
    }

    @Bean
    fun retryer(): Retryer {
        return Retryer.Default(
            retryPeriod,
            maxRetryPeriod,
            maxAttempts
        )
    }

    @Bean
    fun feignLoggerLevel(): Logger.Level {
        return Logger.Level.BASIC
    }

    @Bean
    fun errorDecoder(): ErrorDecoder {
        return FeignErrorDecoder()
    }
}

class FeignErrorDecoder : ErrorDecoder {

    private val defaultDecoder = ErrorDecoder.Default()

    override fun decode(methodKey: String, response: feign.Response): Exception {
        return when (response.status()) {
            400 -> IllegalArgumentException("Bad request to external service: $methodKey")
            404 -> NoSuchElementException("Resource not found: $methodKey")
            429 -> RuntimeException("Rate limit exceeded for: $methodKey")
            in 500..599 -> RuntimeException("External service error (${response.status()}): $methodKey")
            else -> defaultDecoder.decode(methodKey, response)
        }
    }
}
