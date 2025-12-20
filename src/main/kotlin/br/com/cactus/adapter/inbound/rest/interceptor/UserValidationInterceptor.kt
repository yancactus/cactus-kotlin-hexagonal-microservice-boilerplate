package br.com.cactus.adapter.inbound.rest.interceptor

import br.com.cactus.core.config.CacheKeys
import br.com.cactus.core.config.CacheTtl
import br.com.cactus.core.config.HttpHeaders
import br.com.cactus.core.ports.output.CachePort
import br.com.cactus.core.ports.output.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.util.UUID

@Component
class UserValidationInterceptor(
    private val userRepository: UserRepository,
    private val cachePort: CachePort
) : HandlerInterceptor {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val userIdHeader = request.getHeader(HttpHeaders.USER_ID)

        if (userIdHeader.isNullOrBlank()) {
            logger.warn("Missing ${HttpHeaders.USER_ID} header for ${request.method} ${request.requestURI}")
            sendError(response, HttpStatus.BAD_REQUEST, "Missing required header: ${HttpHeaders.USER_ID}")
            return false
        }

        val userId = try {
            UUID.fromString(userIdHeader)
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid UUID format in ${HttpHeaders.USER_ID}: $userIdHeader")
            sendError(response, HttpStatus.BAD_REQUEST, "Invalid UUID format in ${HttpHeaders.USER_ID} header")
            return false
        }

        val userExists = runBlocking { checkUserExists(userId) }

        if (!userExists) {
            logger.warn("User not found: $userId")
            sendError(response, HttpStatus.UNAUTHORIZED, "User not found: $userId")
            return false
        }

        request.setAttribute(HttpHeaders.VALIDATED_USER_ID_ATTRIBUTE, userId)
        logger.debug("User validated successfully: $userId")

        return true
    }

    private suspend fun checkUserExists(userId: UUID): Boolean {
        val cacheKey = CacheKeys.userExists(userId)

        val cachedResult = cachePort.get(cacheKey, Boolean::class.java)
        if (cachedResult != null) {
            logger.debug("User existence cache HIT for $userId: $cachedResult")
            return cachedResult
        }

        logger.debug("User existence cache MISS for $userId, querying database")
        val user = userRepository.findById(userId)
        val exists = user != null && user.active

        cachePort.set(cacheKey, exists, CacheTtl.USER_EXISTS)

        return exists
    }

    private fun sendError(response: HttpServletResponse, status: HttpStatus, message: String) {
        response.status = status.value()
        response.contentType = "application/json"
        response.writer.write("""{"error": "${status.reasonPhrase}", "message": "$message"}""")
    }
}

fun HttpServletRequest.getValidatedUserId(): UUID? {
    return getAttribute(HttpHeaders.VALIDATED_USER_ID_ATTRIBUTE) as? UUID
}

fun HttpServletRequest.requireValidatedUserId(): UUID {
    return getValidatedUserId()
        ?: throw IllegalStateException("No validated user ID found. Ensure UserValidationInterceptor is applied.")
}
