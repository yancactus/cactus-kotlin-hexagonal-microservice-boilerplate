package br.com.cactus.adapter.inbound.rest.controller

import br.com.cactus.core.domain.Address
import br.com.cactus.core.ports.output.AddressLookupPort
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.runBlocking
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/addresses")
@Tag(name = "Addresses", description = "Address lookup endpoints (CEP)")
class AddressController(
    private val addressLookupPort: AddressLookupPort
) {

    @GetMapping("/cep/{cep}")
    @Operation(
        summary = "Lookup address by CEP",
        description = "Retrieves address information for a Brazilian postal code (CEP). " +
            "Results are cached for 7 days."
    )
    fun findByCep(@PathVariable cep: String): ResponseEntity<AddressResponse> = runBlocking {
        val address = addressLookupPort.findByCep(cep)
            ?: return@runBlocking ResponseEntity.notFound().build()

        ResponseEntity.ok(address.toResponse())
    }

    @GetMapping("/cep/{cep}/validate")
    @Operation(
        summary = "Validate CEP",
        description = "Checks if a CEP is valid (exists in the postal system)."
    )
    fun validateCep(@PathVariable cep: String): ResponseEntity<CepValidationResponse> = runBlocking {
        val isValid = addressLookupPort.isValidCep(cep)
        ResponseEntity.ok(CepValidationResponse(cep, isValid))
    }
}

data class AddressResponse(
    val cep: String,
    val street: String,
    val complement: String?,
    val neighborhood: String,
    val city: String,
    val state: String,
    val ibgeCode: String?,
    val fullAddress: String
)

data class CepValidationResponse(
    val cep: String,
    val valid: Boolean
)

private fun Address.toResponse() = AddressResponse(
    cep = cep,
    street = street,
    complement = complement,
    neighborhood = neighborhood,
    city = city,
    state = state,
    ibgeCode = ibgeCode,
    fullAddress = toFullAddress()
)
