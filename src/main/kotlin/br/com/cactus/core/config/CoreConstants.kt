package br.com.cactus.core.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object CacheKeys {
    const val USER_PREFIX = "user:"
    const val PRODUCT_PREFIX = "product:"
    const val PRODUCT_STOCK_PREFIX = "product:stock:"
    const val ADDRESS_CEP_PREFIX = "address:cep:"
    const val USER_EXISTS_PREFIX = "user:exists:"

    fun user(id: Any): String = "$USER_PREFIX$id"
    fun product(id: String): String = "$PRODUCT_PREFIX$id"
    fun productStock(id: String): String = "$PRODUCT_STOCK_PREFIX$id"
    fun addressCep(cep: String): String = "$ADDRESS_CEP_PREFIX$cep"
    fun userExists(id: Any): String = "$USER_EXISTS_PREFIX$id"
}

object LockKeys {
    const val LOCK_PREFIX = "lock:"

    fun forResource(resourceId: String): String = "$LOCK_PREFIX$resourceId"
}

object CacheTtl {
    val USER: Duration = 30.minutes
    val PRODUCT: Duration = 1.hours
    val ADDRESS: Duration = 7.days
    val USER_EXISTS: Duration = 1.hours
}

object LockConfig {
    val DEFAULT_WAIT_TIME: Duration = 5.seconds
    val DEFAULT_LEASE_TIME: Duration = 30.seconds
    val STOCK_OPERATION_WAIT_TIME: Duration = 10.seconds
    val STOCK_OPERATION_LEASE_TIME: Duration = 30.seconds
    val ORDER_OPERATION_WAIT_TIME: Duration = 15.seconds
    val ORDER_OPERATION_LEASE_TIME: Duration = 60.seconds
}

object OptimisticLockConfig {
    const val MAX_RETRIES = 3
    val INITIAL_BACKOFF: Duration = 50.milliseconds
    val MAX_BACKOFF: Duration = 500.milliseconds
    const val BACKOFF_MULTIPLIER = 2.0
}

object SchedulerConfig {
    const val ORDER_EXPIRATION_LOCK_NAME = "order-expiration-check"
    const val ORDER_DAILY_STATS_LOCK_NAME = "order-daily-stats"
    const val ORDER_EXPIRATION_LOCK_AT_MOST = "PT30M"
    const val ORDER_EXPIRATION_LOCK_AT_LEAST = "PT5M"
    const val ORDER_STATS_LOCK_AT_MOST = "PT1H"
    const val ORDER_STATS_LOCK_AT_LEAST = "PT10M"
}

object HttpHeaders {
    const val USER_ID = "X-User-Id"
    const val VALIDATED_USER_ID_ATTRIBUTE = "validatedUserId"
}

enum class StockUpdateReason(val template: String) {
    ORDER_CONFIRMED("Order confirmed: %s"),
    ORDER_CANCELLED("Order cancelled: %s");

    fun format(orderId: Any): String = template.format(orderId)
}

object EventTypes {
    const val USER_CREATED = "USER_CREATED"
    const val PRODUCT_CREATED = "PRODUCT_CREATED"
    const val PRODUCT_STOCK_UPDATED = "PRODUCT_STOCK_UPDATED"
    const val ORDER_CREATED = "ORDER_CREATED"
    const val ORDER_STATUS_CHANGED = "ORDER_STATUS_CHANGED"
}

object ApiPaths {
    const val API_V1 = "/api/v1"
    const val USERS = "$API_V1/users/**"
    const val PRODUCTS = "$API_V1/products/**"
    const val ORDERS = "$API_V1/orders/**"
    const val ADDRESSES = "$API_V1/addresses/**"
    const val SWAGGER_UI = "/swagger-ui/**"
    const val SWAGGER_UI_HTML = "/swagger-ui.html"
    const val API_DOCS = "/api-docs/**"
    const val API_DOCS_V3 = "/v3/api-docs/**"
    const val ACTUATOR = "/actuator/**"
    const val ERROR = "/error"
}

object MetricNames {
    const val KAFKA_CONSUMER_PROCESSING_TIME = "kafka.consumer.processing.time"
    const val EVENT_TYPE_TAG = "event_type"
    const val SUCCESS_TAG = "success"
}
