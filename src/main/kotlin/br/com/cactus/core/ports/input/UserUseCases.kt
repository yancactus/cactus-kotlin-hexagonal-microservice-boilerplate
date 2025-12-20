package br.com.cactus.core.ports.input

import br.com.cactus.core.domain.User
import java.util.UUID

interface CreateUserUseCase {
    suspend fun execute(command: CreateUserCommand): User
}

interface GetUserUseCase {
    suspend fun execute(id: UUID): User
}

interface UpdateUserUseCase {
    suspend fun execute(command: UpdateUserCommand): User
}

interface DeactivateUserUseCase {
    suspend fun execute(id: UUID): User
}

interface ListUsersUseCase {
    suspend fun execute(query: ListUsersQuery): PagedResult<User>
}

data class CreateUserCommand(
    val name: String,
    val email: String
)

data class UpdateUserCommand(
    val id: UUID,
    val name: String? = null,
    val email: String? = null
)

data class ListUsersQuery(
    val page: Int = 0,
    val size: Int = 20,
    val activeOnly: Boolean = true
)

data class PagedResult<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)
