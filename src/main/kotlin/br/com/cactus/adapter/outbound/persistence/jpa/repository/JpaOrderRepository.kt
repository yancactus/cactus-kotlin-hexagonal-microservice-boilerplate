package br.com.cactus.adapter.outbound.persistence.jpa.repository

import br.com.cactus.adapter.outbound.persistence.jpa.entity.OrderEntity
import br.com.cactus.core.domain.OrderStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface JpaOrderRepository : JpaRepository<OrderEntity, UUID> {

    fun findAllByStatus(status: OrderStatus, pageable: Pageable): Page<OrderEntity>

    fun findAllByUserId(userId: UUID, pageable: Pageable): Page<OrderEntity>

    fun findAllByUserIdAndStatus(userId: UUID, status: OrderStatus, pageable: Pageable): Page<OrderEntity>

    /**
     * Pessimistic lock for order status transitions.
     * Prevents concurrent modifications during confirm/cancel/complete operations.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderEntity o WHERE o.id = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): Optional<OrderEntity>

    /**
     * Optimistic lock update with version check.
     * Returns number of updated rows (0 if version mismatch).
     */
    @Modifying
    @Query("UPDATE OrderEntity o SET o.status = :status, o.version = o.version + 1, o.updatedAt = CURRENT_TIMESTAMP WHERE o.id = :id AND o.version = :version")
    fun updateStatusWithVersion(
        @Param("id") id: UUID,
        @Param("version") version: Long,
        @Param("status") status: OrderStatus
    ): Int
}
