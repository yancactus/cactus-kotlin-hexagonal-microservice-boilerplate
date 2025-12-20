package br.com.cactus.core.ports.output

import br.com.cactus.core.domain.Address

interface AddressLookupPort {

    suspend fun findByCep(cep: String): Address?

    suspend fun isValidCep(cep: String): Boolean = findByCep(cep) != null
}

class AddressLookupException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
