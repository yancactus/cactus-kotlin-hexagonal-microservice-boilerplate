package br.com.cactus.adapter.outbound.client

import br.com.cactus.core.config.CacheKeys
import br.com.cactus.core.config.CacheTtl
import br.com.cactus.core.domain.Address
import br.com.cactus.core.ports.output.AddressLookupException
import br.com.cactus.core.ports.output.AddressLookupPort
import br.com.cactus.core.ports.output.CachePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AddressLookupAdapter(
    private val brasilApiClient: BrasilApiFeignClient,
    private val cachePort: CachePort
) : AddressLookupPort {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun findByCep(cep: String): Address? = withContext(Dispatchers.IO) {
        val normalizedCep = normalizeCep(cep)
        val cacheKey = CacheKeys.addressCep(normalizedCep)

        try {
            val cached = cachePort.get(cacheKey, Address::class.java)
            if (cached != null) {
                logger.debug("Address cache HIT for CEP: $normalizedCep")
                return@withContext cached
            }
        } catch (e: Exception) {
            logger.warn("Cache read failed for CEP: $normalizedCep", e)
        }

        logger.debug("Address cache MISS for CEP: $normalizedCep, calling BrasilAPI")

        try {
            val response = brasilApiClient.findByCep(normalizedCep)

            if (!response.isValid()) {
                logger.debug("Invalid CEP: $normalizedCep")
                return@withContext null
            }

            val address = mapToAddress(response)

            try {
                cachePort.set(cacheKey, address, CacheTtl.ADDRESS)
            } catch (e: Exception) {
                logger.warn("Failed to cache address for CEP: $normalizedCep", e)
            }

            return@withContext address

        } catch (e: NoSuchElementException) {
            logger.debug("CEP not found: $normalizedCep")
            return@withContext null

        } catch (e: Exception) {
            logger.error("Failed to lookup CEP: $normalizedCep", e)
            throw AddressLookupException("Failed to lookup CEP: $normalizedCep", e)
        }
    }

    private fun normalizeCep(cep: String): String {
        return cep.replace(Regex("[^0-9]"), "")
    }

    private fun mapToAddress(response: BrasilApiCepResponse): Address {
        val formattedCep = response.cep?.let { formatCep(it) }
            ?: throw IllegalStateException("CEP is null")

        return Address(
            cep = formattedCep,
            street = response.street?.takeIf { it.isNotBlank() } ?: "N/A",
            complement = null,
            neighborhood = response.neighborhood ?: "",
            city = response.city ?: throw IllegalStateException("City is null"),
            state = response.state ?: throw IllegalStateException("State is null"),
            ibgeCode = null
        )
    }

    private fun formatCep(cep: String): String {
        val digits = cep.replace(Regex("[^0-9]"), "")
        return if (digits.length == 8) {
            "${digits.substring(0, 5)}-${digits.substring(5)}"
        } else {
            digits
        }
    }
}
