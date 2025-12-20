package br.com.cactus.core.usecase

import br.com.cactus.core.config.CacheKeys
import br.com.cactus.core.config.CacheTtl
import br.com.cactus.core.domain.User
import br.com.cactus.core.domain.UserCreatedEvent
import br.com.cactus.core.exception.DuplicateEntityException
import br.com.cactus.core.exception.EntityNotFoundException
import br.com.cactus.core.ports.input.*
import br.com.cactus.core.ports.output.CachePort
import br.com.cactus.core.ports.output.EventPublisher
import br.com.cactus.core.ports.output.UserRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CreateUserUseCaseImpl(
    private val userRepository: UserRepository,
    private val eventPublisher: EventPublisher,
    private val cachePort: CachePort,
    private val applicationEventPublisher: ApplicationEventPublisher
) : CreateUserUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun execute(command: CreateUserCommand): User = coroutineScope {
        logger.info("Creating user with email: ${command.email}")

        if (userRepository.existsByEmail(command.email)) {
            throw DuplicateEntityException("User", "email", command.email)
        }

        val user = User(
            name = command.name,
            email = command.email
        )

        val savedUser = userRepository.save(user)

        launch {
            cachePort.set(CacheKeys.user(savedUser.id), savedUser, CacheTtl.USER)
            cachePort.set(CacheKeys.userExists(savedUser.id), true, CacheTtl.USER_EXISTS)
        }

        val event = UserCreatedEvent(userId = savedUser.id, email = savedUser.email)

        applicationEventPublisher.publishEvent(event)

        launch {
            eventPublisher.publish(event)
        }

        logger.info("User created successfully: ${savedUser.id}")
        savedUser
    }
}

@Service
class GetUserUseCaseImpl(
    private val userRepository: UserRepository,
    private val cachePort: CachePort
) : GetUserUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun execute(id: UUID): User {
        logger.debug("Getting user: $id")

        return cachePort.getOrPut(CacheKeys.user(id), User::class.java, CacheTtl.USER) {
            userRepository.findById(id)
                ?: throw EntityNotFoundException("User", id)
        }
    }
}

@Service
class UpdateUserUseCaseImpl(
    private val userRepository: UserRepository,
    private val cachePort: CachePort
) : UpdateUserUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun execute(command: UpdateUserCommand): User = coroutineScope {
        logger.info("Updating user: ${command.id}")

        val existingUser = userRepository.findById(command.id)
            ?: throw EntityNotFoundException("User", command.id)

        if (command.email != null && command.email != existingUser.email) {
            if (userRepository.existsByEmail(command.email)) {
                throw DuplicateEntityException("User", "email", command.email)
            }
        }

        val updatedUser = existingUser.update(
            name = command.name,
            email = command.email
        )

        val savedUser = userRepository.save(updatedUser)

        launch {
            cachePort.set(CacheKeys.user(savedUser.id), savedUser, CacheTtl.USER)
        }

        logger.info("User updated successfully: ${savedUser.id}")
        savedUser
    }
}

@Service
class DeactivateUserUseCaseImpl(
    private val userRepository: UserRepository,
    private val cachePort: CachePort
) : DeactivateUserUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun execute(id: UUID): User = coroutineScope {
        logger.info("Deactivating user: $id")

        val user = userRepository.findById(id)
            ?: throw EntityNotFoundException("User", id)

        val deactivatedUser = user.deactivate()
        val savedUser = userRepository.save(deactivatedUser)

        launch {
            cachePort.delete(CacheKeys.user(id))
            cachePort.delete(CacheKeys.userExists(id))
        }

        logger.info("User deactivated: $id")
        savedUser
    }
}

@Service
class ListUsersUseCaseImpl(
    private val userRepository: UserRepository
) : ListUsersUseCase {

    override suspend fun execute(query: ListUsersQuery): PagedResult<User> {
        val pagedData = userRepository.findAll(query.page, query.size, query.activeOnly)

        return PagedResult(
            content = pagedData.content,
            page = query.page,
            size = query.size,
            totalElements = pagedData.totalElements,
            totalPages = pagedData.totalPages
        )
    }
}
