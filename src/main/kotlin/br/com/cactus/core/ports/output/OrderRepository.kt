package br.com.cactus.core.ports.output

import br.com.cactus.core.domain.Order
import br.com.cactus.core.domain.OrderStatus
import java.util.*

interface OrderRepository {
    suspend fun save(order: Order): Order
    suspend fun findById(id: UUID): Order?
    suspend fun findAll(page: Int, size: Int, status: OrderStatus?): PagedData<Order>
    suspend fun findByUserId(userId: UUID, page: Int, size: Int, status: OrderStatus?): PagedData<Order>
    suspend fun findByIdForUpdate(id: UUID): Order?
    suspend fun updateStatus(id: UUID, expectedVersion: Long, newStatus: OrderStatus): Order?
}
