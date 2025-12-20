package br.com.cactus.adapter.inbound.rest.controller

import br.com.cactus.adapter.inbound.rest.dto.*
import br.com.cactus.adapter.inbound.rest.mapper.toCommand
import br.com.cactus.adapter.inbound.rest.mapper.toResponse
import br.com.cactus.core.ports.input.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User management endpoints")
class UserController(
    private val createUserUseCase: CreateUserUseCase,
    private val getUserUseCase: GetUserUseCase,
    private val updateUserUseCase: UpdateUserUseCase,
    private val deactivateUserUseCase: DeactivateUserUseCase,
    private val listUsersUseCase: ListUsersUseCase
) {

    @PostMapping
    @Operation(summary = "Create a new user")
    suspend fun createUser(
        @Valid @RequestBody request: CreateUserRequest
    ): ResponseEntity<UserResponse> {
        val user = createUserUseCase.execute(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(user.toResponse())
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID")
    suspend fun getUser(@PathVariable id: UUID): ResponseEntity<UserResponse> {
        val user = getUserUseCase.execute(id)
        return ResponseEntity.ok(user.toResponse())
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user")
    suspend fun updateUser(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateUserRequest
    ): ResponseEntity<UserResponse> {
        val user = updateUserUseCase.execute(request.toCommand(id))
        return ResponseEntity.ok(user.toResponse())
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate user")
    suspend fun deactivateUser(@PathVariable id: UUID): ResponseEntity<UserResponse> {
        val user = deactivateUserUseCase.execute(id)
        return ResponseEntity.ok(user.toResponse())
    }

    @GetMapping
    @Operation(summary = "List users with pagination")
    suspend fun listUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "true") activeOnly: Boolean
    ): ResponseEntity<PagedResponse<UserResponse>> {
        val result = listUsersUseCase.execute(ListUsersQuery(page, size, activeOnly))
        return ResponseEntity.ok(result.toResponse { it.toResponse() })
    }
}
