package br.com.cactus.adapter.outbound.persistence.jpa

import br.com.cactus.adapter.outbound.persistence.jpa.mapper.toDomain
import br.com.cactus.adapter.outbound.persistence.jpa.mapper.toEntity
import br.com.cactus.adapter.outbound.persistence.jpa.repository.JpaOrderRepository
import br.com.cactus.core.domain.Order
import br.com.cactus.core.domain.OrderStatus
import br.com.cactus.core.ports.output.OrderRepository
import br.com.cactus.core.ports.output.PagedData
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
@Transactional
class OrderRepositoryAdapter(
    private val jpaRepository: JpaOrderRepository
) : OrderRepository {

    override suspend fun save(order: Order): Order {
        return jpaRepository.save(order.toEntity()).toDomain()
    }

    override suspend fun findById(id: UUID): Order? {
        return jpaRepository.findById(id).map { it.toDomain() }.orElse(null)
    }

    override suspend fun findAll(page: Int, size: Int, status: OrderStatus?): PagedData<Order> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val pageResult = if (status != null) {
            jpaRepository.findAllByStatus(status, pageable)
        } else {
            jpaRepository.findAll(pageable)
        }

        return PagedData(
            content = pageResult.content.map { it.toDomain() },
            totalElements = pageResult.totalElements,
            totalPages = pageResult.totalPages
        )
    }

    override suspend fun findByUserId(userId: UUID, page: Int, size: Int, status: OrderStatus?): PagedData<Order> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val pageResult = if (status != null) {
            jpaRepository.findAllByUserIdAndStatus(userId, status, pageable)
        } else {
            jpaRepository.findAllByUserId(userId, pageable)
        }

        return PagedData(
            content = pageResult.content.map { it.toDomain() },
            totalElements = pageResult.totalElements,
            totalPages = pageResult.totalPages
        )
    }

    override suspend fun findByIdForUpdate(id: UUID): Order? {
        return jpaRepository.findByIdForUpdate(id).map { it.toDomain() }.orElse(null)
    }

    override suspend fun updateStatus(id: UUID, expectedVersion: Long, newStatus: OrderStatus): Order? {
        val updated = jpaRepository.updateStatusWithVersion(id, expectedVersion, newStatus)
        return if (updated > 0) {
            jpaRepository.findById(id).map { it.toDomain() }.orElse(null)
        } else {
            null
        }
    }
}
