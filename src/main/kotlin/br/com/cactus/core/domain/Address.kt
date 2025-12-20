package br.com.cactus.core.domain

data class Address(
    val cep: String,
    val street: String,
    val complement: String?,
    val neighborhood: String,
    val city: String,
    val state: String,
    val ibgeCode: String?
) {
    init {
        require(cep.matches(Regex("\\d{5}-?\\d{3}"))) { "Invalid CEP format" }
        require(street.isNotBlank()) { "Street cannot be blank" }
        require(city.isNotBlank()) { "City cannot be blank" }
        require(state.length == 2) { "State must be 2 characters" }
    }

    fun toFullAddress(): String {
        val complementPart = complement?.let { ", $it" } ?: ""
        return "$street$complementPart - $neighborhood, $city - $state, $cep"
    }
}
