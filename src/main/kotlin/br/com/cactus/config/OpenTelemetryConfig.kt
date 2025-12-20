package br.com.cactus.config

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.semconv.ResourceAttributes
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("!test")
class OpenTelemetryConfig(
    @Value("\${spring.application.name:cactus-app}")
    private val applicationName: String,

    @Value("\${otel.exporter.otlp.endpoint:http://localhost:4317}")
    private val otlpEndpoint: String
) {

    @Bean
    fun openTelemetry(): OpenTelemetry {
        val resource = Resource.getDefault()
            .merge(
                Resource.create(
                    io.opentelemetry.api.common.Attributes.of(
                        ResourceAttributes.SERVICE_NAME, applicationName,
                        ResourceAttributes.SERVICE_VERSION, "1.0.0"
                    )
                )
            )

        val spanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint(otlpEndpoint)
            .build()

        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .setResource(resource)
            .build()

        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .buildAndRegisterGlobal()
    }

    @Bean
    fun tracer(openTelemetry: OpenTelemetry): Tracer {
        return openTelemetry.getTracer(applicationName)
    }
}
