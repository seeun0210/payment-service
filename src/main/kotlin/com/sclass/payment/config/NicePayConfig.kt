package com.sclass.payment.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
class NicePayConfig(
    @Value("\${nicepay.client-id:test-client-id}")
    val clientId: String,

    @Value("\${nicepay.secret-key:test-secret-key}")
    val secretKey: String,

    @Value("\${nicepay.base-url:https://sandbox-api.nicepay.co.kr}")
    val baseUrl: String,

    @Value("\${nicepay.return-url:http://localhost:8080/api/payments/return}")
    val returnUrl: String,

    @Value("\${nicepay.cancel-url:http://localhost:8080/api/payments/cancel}")
    val cancelUrl: String
) {
    @Bean
    fun restTemplate() = RestTemplate()
}