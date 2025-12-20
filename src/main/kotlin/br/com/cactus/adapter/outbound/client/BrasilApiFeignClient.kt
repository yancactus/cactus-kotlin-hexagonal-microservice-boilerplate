package br.com.cactus.adapter.outbound.client

import br.com.cactus.config.FeignConfig
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@FeignClient(
    name = "brasil-api-cep",
    url = "\${feign.clients.brasil-api.url:https://brasilapi.com.br/api}",
    configuration = [FeignConfig::class]
)
interface BrasilApiFeignClient {

    @GetMapping("/cep/v2/{cep}")
    fun findByCep(@PathVariable cep: String): BrasilApiCepResponse
}

data class BrasilApiCepResponse(
    @JsonProperty("cep")
    val cep: String? = null,

    @JsonProperty("state")
    val state: String? = null,

    @JsonProperty("city")
    val city: String? = null,

    @JsonProperty("neighborhood")
    val neighborhood: String? = null,

    @JsonProperty("street")
    val street: String? = null,

    @JsonProperty("service")
    val service: String? = null,

    @JsonProperty("location")
    val location: BrasilApiLocation? = null
) {
    fun isValid(): Boolean = cep != null && city != null && state != null
}

data class BrasilApiLocation(
    @JsonProperty("type")
    val type: String? = null,

    @JsonProperty("coordinates")
    val coordinates: BrasilApiCoordinates? = null
)

data class BrasilApiCoordinates(
    @JsonProperty("longitude")
    val longitude: String? = null,

    @JsonProperty("latitude")
    val latitude: String? = null
)
