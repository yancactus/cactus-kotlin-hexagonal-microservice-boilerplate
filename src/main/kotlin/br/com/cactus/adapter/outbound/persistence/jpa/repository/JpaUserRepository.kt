package br.com.cactus.adapter.outbound.persistence.jpa.repository

import br.com.cactus.adapter.outbound.persistence.jpa.entity.UserEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface JpaUserRepository : JpaRepository<UserEntity, UUID> {
    fun findByEmail(email: String): UserEntity?
    fun existsByEmail(email: String): Boolean
    fun findAllByActiveTrue(pageable: Pageable): Page<UserEntity>
}
