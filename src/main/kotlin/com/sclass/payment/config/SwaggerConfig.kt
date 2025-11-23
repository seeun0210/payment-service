package com.sclass.payment.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info  // ✅ 올바른 import
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Payment Service API")
                    .description("Payment Service REST API 문서")
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name("S-Class Team")
                            .email("contact@s-class.com")
                    )
                    .license(
                        License()
                            .name("MIT License")
                            .url("https://opensource.org/licenses/MIT")
                    )
            )
            .servers(
                listOf(
                    Server()
                        .url("http://localhost:8082")
                        .description("Payment Service (직접 접근)"),
                    Server()
                        .url("http://localhost:8765/payment-service")
                        .description("API Gateway를 통한 접근")
                )
            )
    }
}