package br.com.cactus.core.domain

import java.time.Instant
import java.util.UUID

data class User(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val email: String,
    val active: Boolean = true,
    val version: Long = 0,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    init {
        require(name.isNotBlank()) { "Name cannot be blank" }
        require(email.isNotBlank() && email.contains("@")) { "Invalid email" }
    }

    fun update(name: String? = null, email: String? = null) = copy(
        name = name ?: this.name,
        email = email ?: this.email,
        updatedAt = Instant.now()
    )

    fun deactivate() = copy(active = false, updatedAt = Instant.now())
}
