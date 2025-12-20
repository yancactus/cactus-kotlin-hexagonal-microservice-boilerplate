package br.com.cactus.config

import org.springframework.amqp.core.*
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitMQConfig(
    @Value("\${rabbitmq.exchange.domain-events:domain-events-exchange}")
    private val exchangeName: String,

    @Value("\${rabbitmq.queue.domain-events:domain-events-queue}")
    private val queueName: String,

    @Value("\${rabbitmq.queue.dlq:domain-events-dlq}")
    private val dlqName: String
) {

    @Bean
    fun domainEventsExchange(): TopicExchange {
        return TopicExchange(exchangeName, true, false)
    }

    @Bean
    fun domainEventsQueue(): Queue {
        return QueueBuilder.durable(queueName)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", dlqName)
            .build()
    }

    @Bean
    fun deadLetterQueue(): Queue {
        return QueueBuilder.durable(dlqName).build()
    }

    @Bean
    fun binding(): Binding {
        return BindingBuilder
            .bind(domainEventsQueue())
            .to(domainEventsExchange())
            .with("domain.event.#")
    }

    @Bean
    fun jackson2JsonMessageConverter(): Jackson2JsonMessageConverter {
        return Jackson2JsonMessageConverter()
    }

    @Bean
    fun rabbitTemplate(connectionFactory: ConnectionFactory): RabbitTemplate {
        val template = RabbitTemplate(connectionFactory)
        template.messageConverter = jackson2JsonMessageConverter()
        return template
    }

    @Bean
    fun rabbitListenerContainerFactory(
        connectionFactory: ConnectionFactory
    ): SimpleRabbitListenerContainerFactory {
        val factory = SimpleRabbitListenerContainerFactory()
        factory.setConnectionFactory(connectionFactory)
        factory.setMessageConverter(jackson2JsonMessageConverter())
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL)
        factory.setConcurrentConsumers(3)
        factory.setMaxConcurrentConsumers(10)
        factory.setPrefetchCount(10)
        return factory
    }
}
