package br.com.cactus.adapter.outbound.persistence.jpa.entity

import br.com.cactus.core.domain.OrderStatus
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "orders", indexes = [
    Index(name = "idx_orders_user_id", columnList = "userId"),
    Index(name = "idx_orders_status", columnList = "status")
])
class OrderEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.PENDING,

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id")
    var items: MutableList<OrderItemEntity> = mutableListOf(),

    @Version
    var version: Long = 0,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "order_items")
class OrderItemEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 100)
    val productId: String,

    @Column(nullable = false, length = 200)
    val productName: String,

    @Column(nullable = false)
    val quantity: Int,

    @Column(nullable = false, precision = 19, scale = 4)
    val unitPrice: BigDecimal,

    @Column(nullable = false, precision = 19, scale = 4)
    val subtotal: BigDecimal
)
