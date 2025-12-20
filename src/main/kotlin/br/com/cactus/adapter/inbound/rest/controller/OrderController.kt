package br.com.cactus.adapter.inbound.rest.controller

import br.com.cactus.adapter.inbound.rest.dto.*
import br.com.cactus.adapter.inbound.rest.mapper.toCommand
import br.com.cactus.adapter.inbound.rest.mapper.toResponse
import br.com.cactus.core.domain.OrderStatus
import br.com.cactus.core.ports.input.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Order management endpoints")
class OrderController(
    private val createOrderUseCase: CreateOrderUseCase,
    private val getOrderUseCase: GetOrderUseCase,
    private val listOrdersUseCase: ListOrdersUseCase,
    private val listUserOrdersUseCase: ListUserOrdersUseCase,
    private val confirmOrderUseCase: ConfirmOrderUseCase,
    private val cancelOrderUseCase: CancelOrderUseCase,
    private val completeOrderUseCase: CompleteOrderUseCase
) {

    @PostMapping
    @Operation(summary = "Create a new order")
    suspend fun createOrder(
        @Valid @RequestBody request: CreateOrderRequest
    ): ResponseEntity<OrderResponse> {
        val order = createOrderUseCase.execute(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(order.toResponse())
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID")
    suspend fun getOrder(@PathVariable id: UUID): ResponseEntity<OrderResponse> {
        val order = getOrderUseCase.execute(id)
        return ResponseEntity.ok(order.toResponse())
    }

    @GetMapping
    @Operation(summary = "List all orders with pagination")
    suspend fun listOrders(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) status: OrderStatus?
    ): ResponseEntity<PagedResponse<OrderResponse>> {
        val result = listOrdersUseCase.execute(ListOrdersQuery(page, size, status))
        return ResponseEntity.ok(result.toResponse { it.toResponse() })
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "List orders for a specific user")
    suspend fun listUserOrders(
        @PathVariable userId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) status: OrderStatus?
    ): ResponseEntity<PagedResponse<OrderResponse>> {
        val result = listUserOrdersUseCase.execute(userId, ListOrdersQuery(page, size, status))
        return ResponseEntity.ok(result.toResponse { it.toResponse() })
    }

    @PostMapping("/{id}/confirm")
    @Operation(
        summary = "Confirm order (with multi-resource locking)",
        description = "Confirms order and atomically reserves stock for all items using distributed locks"
    )
    suspend fun confirmOrder(@PathVariable id: UUID): ResponseEntity<OrderResponse> {
        val order = confirmOrderUseCase.execute(id)
        return ResponseEntity.ok(order.toResponse())
    }

    @PostMapping("/{id}/cancel")
    @Operation(
        summary = "Cancel order",
        description = "Cancels order and restores stock if order was confirmed"
    )
    suspend fun cancelOrder(@PathVariable id: UUID): ResponseEntity<OrderResponse> {
        val order = cancelOrderUseCase.execute(id)
        return ResponseEntity.ok(order.toResponse())
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete order (mark as delivered)")
    suspend fun completeOrder(@PathVariable id: UUID): ResponseEntity<OrderResponse> {
        val order = completeOrderUseCase.execute(id)
        return ResponseEntity.ok(order.toResponse())
    }
}
