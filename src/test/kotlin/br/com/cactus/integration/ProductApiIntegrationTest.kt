package br.com.cactus.integration

import br.com.cactus.adapter.inbound.rest.dto.CreateProductRequest
import br.com.cactus.adapter.inbound.rest.dto.StockOperationRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestConfig::class)
class ProductApiIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun performAsyncPost(uri: String, body: Any): MvcResult {
        val asyncResult = mockMvc.perform(
            post(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        return mockMvc.perform(asyncDispatch(asyncResult)).andReturn()
    }

    private fun performAsyncGet(uri: String): MvcResult {
        val asyncResult = mockMvc.perform(get(uri))
            .andExpect(request().asyncStarted())
            .andReturn()

        return mockMvc.perform(asyncDispatch(asyncResult)).andReturn()
    }

    @Test
    fun `should create product successfully`() {
        val request = CreateProductRequest(
            sku = "SKU-${System.currentTimeMillis()}",
            name = "Test Product",
            description = "Test Description",
            price = BigDecimal("99.99"),
            initialStock = 100
        )

        val asyncResult = mockMvc.perform(
            post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(asyncResult))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.sku").value(request.sku))
            .andExpect(jsonPath("$.name").value(request.name))
            .andExpect(jsonPath("$.stock").value(request.initialStock))
    }

    @Test
    fun `should return bad request for negative price`() {
        val request = mapOf(
            "sku" to "SKU-TEST",
            "name" to "Test Product",
            "price" to -10.00,
            "initialStock" to 100
        )

        // Validation errors are synchronous, not async
        mockMvc.perform(
            post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return conflict for duplicate SKU`() {
        val sku = "SKU-DUP-${System.currentTimeMillis()}"
        val request = CreateProductRequest(
            sku = sku,
            name = "Product 1",
            price = BigDecimal("50.00"),
            initialStock = 50
        )

        // Create first product
        val firstResult = mockMvc.perform(
            post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(firstResult))
            .andExpect(status().isCreated)

        // Try to create duplicate
        val duplicateResult = mockMvc.perform(
            post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request.copy(name = "Product 2")))
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(duplicateResult))
            .andExpect(status().isConflict)
    }

    @Test
    fun `should get product by id`() {
        val request = CreateProductRequest(
            sku = "SKU-GET-${System.currentTimeMillis()}",
            name = "Get Test Product",
            price = BigDecimal("75.00"),
            initialStock = 25
        )

        val createAsyncResult = mockMvc.perform(
            post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        val createResult = mockMvc.perform(asyncDispatch(createAsyncResult))
            .andExpect(status().isCreated)
            .andReturn()

        val productId = objectMapper.readTree(createResult.response.contentAsString).get("id").asText()

        val getAsyncResult = mockMvc.perform(get("/api/v1/products/$productId"))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(getAsyncResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(productId))
            .andExpect(jsonPath("$.sku").value(request.sku))
    }

    @Test
    fun `should reserve stock successfully`() {
        val createRequest = CreateProductRequest(
            sku = "SKU-RESERVE-${System.currentTimeMillis()}",
            name = "Reserve Stock Product",
            price = BigDecimal("100.00"),
            initialStock = 50
        )

        val createAsyncResult = mockMvc.perform(
            post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        val createResult = mockMvc.perform(asyncDispatch(createAsyncResult))
            .andExpect(status().isCreated)
            .andReturn()

        val productId = objectMapper.readTree(createResult.response.contentAsString).get("id").asText()

        val reserveRequest = StockOperationRequest(
            quantity = 10,
            reason = "Order #123"
        )

        val reserveAsyncResult = mockMvc.perform(
            post("/api/v1/products/$productId/stock/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reserveRequest))
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(reserveAsyncResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.stock").value(40))
    }

    @Test
    fun `should return bad request for insufficient stock`() {
        val createRequest = CreateProductRequest(
            sku = "SKU-INSUF-${System.currentTimeMillis()}",
            name = "Low Stock Product",
            price = BigDecimal("100.00"),
            initialStock = 5
        )

        val createAsyncResult = mockMvc.perform(
            post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        val createResult = mockMvc.perform(asyncDispatch(createAsyncResult))
            .andExpect(status().isCreated)
            .andReturn()

        val productId = objectMapper.readTree(createResult.response.contentAsString).get("id").asText()

        val reserveRequest = StockOperationRequest(
            quantity = 100,
            reason = "Large order"
        )

        val reserveAsyncResult = mockMvc.perform(
            post("/api/v1/products/$productId/stock/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reserveRequest))
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(reserveAsyncResult))
            .andExpect(status().isUnprocessableEntity)
    }

    @Test
    fun `should list products with pagination`() {
        val asyncResult = mockMvc.perform(get("/api/v1/products?page=0&size=10"))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(asyncResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(10))
    }
}
