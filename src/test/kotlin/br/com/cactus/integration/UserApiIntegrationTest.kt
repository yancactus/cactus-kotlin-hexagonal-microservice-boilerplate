package br.com.cactus.integration

import br.com.cactus.adapter.inbound.rest.dto.CreateUserRequest
import br.com.cactus.adapter.inbound.rest.dto.UpdateUserRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestConfig::class)
@ContextConfiguration(initializers = [TestcontainersInitializer::class])
class UserApiIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `should create user successfully`() {
        val request = CreateUserRequest(
            name = "Test User",
            email = "test${System.currentTimeMillis()}@example.com"
        )

        val asyncResult = mockMvc.perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(asyncResult))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value(request.name))
            .andExpect(jsonPath("$.email").value(request.email))
            .andExpect(jsonPath("$.active").value(true))
    }

    @Test
    fun `should return bad request for invalid email`() {
        val request = mapOf(
            "name" to "Test User",
            "email" to "invalid-email"
        )

        // Validation errors are synchronous, not async
        mockMvc.perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return conflict for duplicate email`() {
        val email = "duplicate${System.currentTimeMillis()}@example.com"
        val request = CreateUserRequest(name = "User 1", email = email)

        val firstResult = mockMvc.perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(firstResult))
            .andExpect(status().isCreated)

        val duplicateResult = mockMvc.perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request.copy(name = "User 2")))
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(duplicateResult))
            .andExpect(status().isConflict)
    }

    @Test
    fun `should get user by id`() {
        val request = CreateUserRequest(
            name = "Get Test User",
            email = "gettest${System.currentTimeMillis()}@example.com"
        )

        val createAsyncResult = mockMvc.perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        val createResult = mockMvc.perform(asyncDispatch(createAsyncResult))
            .andExpect(status().isCreated)
            .andReturn()

        val userId = objectMapper.readTree(createResult.response.contentAsString).get("id").asText()

        val getAsyncResult = mockMvc.perform(get("/api/v1/users/$userId"))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(getAsyncResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.name").value(request.name))
    }

    @Test
    fun `should return not found for non-existent user`() {
        val asyncResult = mockMvc.perform(get("/api/v1/users/00000000-0000-0000-0000-000000000000"))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(asyncResult))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `should update user successfully`() {
        val createRequest = CreateUserRequest(
            name = "Original Name",
            email = "update${System.currentTimeMillis()}@example.com"
        )

        val createAsyncResult = mockMvc.perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        val createResult = mockMvc.perform(asyncDispatch(createAsyncResult))
            .andExpect(status().isCreated)
            .andReturn()

        val userId = objectMapper.readTree(createResult.response.contentAsString).get("id").asText()

        val updateRequest = UpdateUserRequest(name = "Updated Name", email = null)

        val updateAsyncResult = mockMvc.perform(
            put("/api/v1/users/$userId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest))
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(updateAsyncResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Updated Name"))
            .andExpect(jsonPath("$.email").value(createRequest.email))
    }

    @Test
    fun `should list users with pagination`() {
        val asyncResult = mockMvc.perform(get("/api/v1/users?page=0&size=10"))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(asyncResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(10))
    }
}
