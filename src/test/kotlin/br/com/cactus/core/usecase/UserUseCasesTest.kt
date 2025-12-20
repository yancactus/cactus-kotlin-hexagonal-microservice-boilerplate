package br.com.cactus.core.usecase

import br.com.cactus.core.domain.User
import br.com.cactus.core.exception.DuplicateEntityException
import br.com.cactus.core.exception.EntityNotFoundException
import br.com.cactus.core.ports.input.CreateUserCommand
import br.com.cactus.core.ports.input.UpdateUserCommand
import br.com.cactus.core.ports.output.CachePort
import br.com.cactus.core.ports.output.EventPublisher
import br.com.cactus.core.ports.output.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.springframework.context.ApplicationEventPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class UserUseCasesTest {

    private lateinit var userRepository: UserRepository
    private lateinit var cachePort: CachePort
    private lateinit var eventPublisher: EventPublisher
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @BeforeEach
    fun setup() {
        userRepository = mockk()
        cachePort = mockk()
        eventPublisher = mockk()
        applicationEventPublisher = mockk(relaxed = true)
    }

    @Nested
    inner class CreateUserUseCaseTest {
        private lateinit var useCase: CreateUserUseCaseImpl

        @BeforeEach
        fun setup() {
            useCase = CreateUserUseCaseImpl(userRepository, eventPublisher, cachePort, applicationEventPublisher)
        }

        @Test
        fun `should create user successfully`() = runTest {
            val command = CreateUserCommand(
                name = "John Doe",
                email = "john@example.com"
            )

            val savedUser = User(
                id = UUID.randomUUID(),
                name = command.name,
                email = command.email
            )

            coEvery { userRepository.existsByEmail(command.email) } returns false
            coEvery { userRepository.save(any()) } returns savedUser
            coEvery { cachePort.set(any(), any(), any()) } just runs
            coEvery { eventPublisher.publish(any()) } just runs

            val result = useCase.execute(command)

            assertNotNull(result.id)
            assertEquals(command.name, result.name)
            assertEquals(command.email, result.email)

            coVerify { userRepository.existsByEmail(command.email) }
            coVerify { userRepository.save(any()) }
            coVerify { eventPublisher.publish(any()) }
        }

        @Test
        fun `should throw DuplicateEntityException when email exists`() = runTest {
            val command = CreateUserCommand(
                name = "John Doe",
                email = "existing@example.com"
            )

            coEvery { userRepository.existsByEmail(command.email) } returns true

            assertThrows<DuplicateEntityException> {
                useCase.execute(command)
            }
        }
    }

    @Nested
    inner class GetUserUseCaseTest {
        private lateinit var useCase: GetUserUseCaseImpl

        @BeforeEach
        fun setup() {
            useCase = GetUserUseCaseImpl(userRepository, cachePort)
        }

        @Test
        fun `should return user from cache when available`() = runTest {
            val userId = UUID.randomUUID()
            val cachedUser = User(
                id = userId,
                name = "Cached User",
                email = "cached@example.com"
            )

            coEvery { cachePort.getOrPut(any(), User::class.java, any(), any()) } returns cachedUser

            val result = useCase.execute(userId)

            assertEquals(cachedUser.id, result.id)
            assertEquals(cachedUser.name, result.name)
        }

        @Test
        fun `should throw EntityNotFoundException when user not found`() = runTest {
            val userId = UUID.randomUUID()
            val computeSlot = slot<suspend () -> User>()

            coEvery { cachePort.getOrPut(any(), User::class.java, any(), capture(computeSlot)) } coAnswers {
                computeSlot.captured.invoke()
            }
            coEvery { userRepository.findById(userId) } returns null

            assertThrows<EntityNotFoundException> {
                useCase.execute(userId)
            }
        }
    }

    @Nested
    inner class UpdateUserUseCaseTest {
        private lateinit var useCase: UpdateUserUseCaseImpl

        @BeforeEach
        fun setup() {
            useCase = UpdateUserUseCaseImpl(userRepository, cachePort)
        }

        @Test
        fun `should update user successfully`() = runTest {
            val userId = UUID.randomUUID()
            val existingUser = User(
                id = userId,
                name = "Old Name",
                email = "old@example.com"
            )

            val command = UpdateUserCommand(
                id = userId,
                name = "New Name",
                email = null
            )

            val updatedUser = existingUser.copy(name = "New Name")

            coEvery { userRepository.findById(userId) } returns existingUser
            coEvery { userRepository.save(any()) } returns updatedUser
            coEvery { cachePort.set(any(), any(), any()) } just runs

            val result = useCase.execute(command)

            assertEquals("New Name", result.name)
            assertEquals(existingUser.email, result.email)
        }

        @Test
        fun `should throw DuplicateEntityException when updating to existing email`() = runTest {
            val userId = UUID.randomUUID()
            val existingUser = User(
                id = userId,
                name = "John",
                email = "john@example.com"
            )

            val command = UpdateUserCommand(
                id = userId,
                name = null,
                email = "taken@example.com"
            )

            coEvery { userRepository.findById(userId) } returns existingUser
            coEvery { userRepository.existsByEmail("taken@example.com") } returns true

            assertThrows<DuplicateEntityException> {
                useCase.execute(command)
            }
        }

        @Test
        fun `should throw EntityNotFoundException when user not found`() = runTest {
            val userId = UUID.randomUUID()
            val command = UpdateUserCommand(
                id = userId,
                name = "New Name",
                email = null
            )

            coEvery { userRepository.findById(userId) } returns null

            assertThrows<EntityNotFoundException> {
                useCase.execute(command)
            }
        }
    }

    @Nested
    inner class DeactivateUserUseCaseTest {
        private lateinit var useCase: DeactivateUserUseCaseImpl

        @BeforeEach
        fun setup() {
            useCase = DeactivateUserUseCaseImpl(userRepository, cachePort)
        }

        @Test
        fun `should deactivate user successfully`() = runTest {
            val userId = UUID.randomUUID()
            val activeUser = User(
                id = userId,
                name = "Active User",
                email = "active@example.com",
                active = true
            )

            val deactivatedUser = activeUser.copy(active = false)

            coEvery { userRepository.findById(userId) } returns activeUser
            coEvery { userRepository.save(any()) } returns deactivatedUser
            coEvery { cachePort.delete(any()) } just runs

            val result = useCase.execute(userId)

            assertFalse(result.active)
            coVerify { cachePort.delete(any()) }
        }

        @Test
        fun `should throw EntityNotFoundException when user not found`() = runTest {
            val userId = UUID.randomUUID()

            coEvery { userRepository.findById(userId) } returns null

            assertThrows<EntityNotFoundException> {
                useCase.execute(userId)
            }
        }
    }
}
