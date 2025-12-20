package br.com.cactus.adapter.inbound.rest.exception

import br.com.cactus.core.exception.*
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleNotFound(ex: EntityNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn("Entity not found: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(
                status = HttpStatus.NOT_FOUND.value(),
                error = "Not Found",
                message = ex.message ?: "Entity not found"
            ))
    }

    @ExceptionHandler(DuplicateEntityException::class)
    fun handleDuplicate(ex: DuplicateEntityException): ResponseEntity<ErrorResponse> {
        logger.warn("Duplicate entity: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse(
                status = HttpStatus.CONFLICT.value(),
                error = "Conflict",
                message = ex.message ?: "Entity already exists"
            ))
    }

    @ExceptionHandler(InsufficientStockException::class)
    fun handleInsufficientStock(ex: InsufficientStockException): ResponseEntity<ErrorResponse> {
        logger.warn("Insufficient stock: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse(
                status = HttpStatus.UNPROCESSABLE_ENTITY.value(),
                error = "Insufficient Stock",
                message = ex.message ?: "Insufficient stock",
                details = mapOf(
                    "productId" to ex.productId,
                    "availableStock" to ex.availableStock,
                    "requestedQuantity" to ex.requestedQuantity
                )
            ))
    }

    @ExceptionHandler(InvalidOrderStateException::class)
    fun handleInvalidOrderState(ex: InvalidOrderStateException): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid order state transition: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse(
                status = HttpStatus.UNPROCESSABLE_ENTITY.value(),
                error = "Invalid State Transition",
                message = ex.message ?: "Invalid order state transition",
                details = mapOf(
                    "orderId" to ex.orderId,
                    "currentStatus" to ex.currentStatus,
                    "targetStatus" to ex.targetStatus
                )
            ))
    }

    @ExceptionHandler(ConcurrencyException::class)
    fun handleConcurrency(ex: ConcurrencyException): ResponseEntity<ErrorResponse> {
        logger.warn("Concurrency conflict: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse(
                status = HttpStatus.CONFLICT.value(),
                error = "Concurrency Conflict",
                message = ex.message ?: "Resource was modified by another transaction"
            ))
    }

    @ExceptionHandler(DistributedLockException::class)
    fun handleLockFailure(ex: DistributedLockException): ResponseEntity<ErrorResponse> {
        logger.warn("Lock acquisition failed: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse(
                status = HttpStatus.SERVICE_UNAVAILABLE.value(),
                error = "Lock Acquisition Failed",
                message = "Unable to acquire lock. Please try again."
            ))
    }

    @ExceptionHandler(ValidationException::class)
    fun handleValidation(ex: ValidationException): ResponseEntity<ErrorResponse> {
        logger.warn("Validation error: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = "Validation Error",
                message = ex.message ?: "Validation failed"
            ))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "Invalid") }
        logger.warn("Validation errors: $errors")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = "Validation Error",
                message = "Request validation failed",
                details = errors
            ))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.warn("Illegal argument: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = "Bad Request",
                message = ex.message ?: "Invalid request"
            ))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(
                status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                error = "Internal Server Error",
                message = "An unexpected error occurred"
            ))
    }
}

data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val timestamp: Instant = Instant.now(),
    val details: Map<String, Any>? = null
)
