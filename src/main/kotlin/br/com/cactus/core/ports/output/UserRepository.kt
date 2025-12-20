package br.com.cactus.core.ports.output

import br.com.cactus.core.domain.User
import java.util.UUID

interface UserRepository {
    suspend fun save(user: User): User
    suspend fun findById(id: UUID): User?
    suspend fun findByEmail(email: String): User?
    suspend fun findAll(page: Int, size: Int, activeOnly: Boolean): PagedData<User>
    suspend fun existsByEmail(email: String): Boolean
    suspend fun delete(id: UUID)
}

data class PagedData<T>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int
)
