package br.com.cactus.config

import br.com.cactus.adapter.inbound.rest.interceptor.UserValidationInterceptor
import br.com.cactus.core.config.ApiPaths
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val userValidationInterceptor: UserValidationInterceptor
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(userValidationInterceptor)
            .addPathPatterns(ApiPaths.ORDERS)
            .excludePathPatterns(
                ApiPaths.USERS,
                ApiPaths.PRODUCTS,
                ApiPaths.SWAGGER_UI,
                ApiPaths.SWAGGER_UI_HTML,
                ApiPaths.API_DOCS,
                ApiPaths.API_DOCS_V3,
                ApiPaths.ACTUATOR,
                ApiPaths.ERROR
            )
    }
}
