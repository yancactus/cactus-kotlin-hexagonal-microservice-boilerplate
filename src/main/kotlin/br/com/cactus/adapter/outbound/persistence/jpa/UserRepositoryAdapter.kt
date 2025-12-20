package br.com.cactus.adapter.outbound.persistence.jpa

import br.com.cactus.adapter.outbound.persistence.jpa.mapper.toDomain
import br.com.cactus.adapter.outbound.persistence.jpa.mapper.toEntity
import br.com.cactus.adapter.outbound.persistence.jpa.repository.JpaUserRepository
import br.com.cactus.core.domain.User
import br.com.cactus.core.ports.output.PagedData
import br.com.cactus.core.ports.output.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
@Transactional
class UserRepositoryAdapter(
    private val jpaRepository: JpaUserRepository
) : UserRepository {

    override suspend fun save(user: User): User {
        return jpaRepository.save(user.toEntity()).toDomain()
    }

    override suspend fun findById(id: UUID): User? {
        return jpaRepository.findById(id).map { it.toDomain() }.orElse(null)
    }

    override suspend fun findByEmail(email: String): User? {
        return jpaRepository.findByEmail(email)?.toDomain()
    }

    override suspend fun findAll(page: Int, size: Int, activeOnly: Boolean): PagedData<User> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val pageResult = if (activeOnly) {
            jpaRepository.findAllByActiveTrue(pageable)
        } else {
            jpaRepository.findAll(pageable)
        }

        return PagedData(
            content = pageResult.content.map { it.toDomain() },
            totalElements = pageResult.totalElements,
            totalPages = pageResult.totalPages
        )
    }

    override suspend fun existsByEmail(email: String): Boolean {
        return jpaRepository.existsByEmail(email)
    }

    override suspend fun delete(id: UUID) {
        jpaRepository.deleteById(id)
    }
}
