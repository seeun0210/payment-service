package com.sclass.payment.service

import com.sclass.payment.config.NicePayConfig
import com.sclass.payment.entity.Payment
import org.slf4j.LoggerFactory
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.retry.Retry
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.function.Supplier

@Service
class NicePayService(
    private val nicePayConfig: NicePayConfig,
    private val webClient: WebClient,
    private val circuitBreakerFactory: CircuitBreakerFactory<*, *>
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val circuitBreaker: CircuitBreaker = circuitBreakerFactory.create("nicepay")

    private val retrySpec = Retry.backoff(3, Duration.ofSeconds(1))
        .maxBackoff(Duration.ofSeconds(10))
        .multiplier(2.0)
        .filter { it is RuntimeException }
        .doBeforeRetry { retrySignal ->
            log.warn(
                "나이스페이 API 재시도 - 시도 횟수: {}, 오류: {}",
                retrySignal.totalRetries() + 1,
                retrySignal.failure().message
            )
        }


    fun approvePayment(payment: Payment, tid: String, authToken: String, amount: String) {
        log.info(
            "나이스페이 승인 요청 - orderId: {}, tid: {}, amount: {}",
            payment.pgOrderId,
            tid,
            amount
        )

        val transactionId = getTransactionId(tid, payment.pgOrderId)
        val requestData = createRequestData(payment, authToken, amount)
        val apiUrl = "${nicePayConfig.baseUrl}/v1/payments/$transactionId"

        logRequestDetails(apiUrl, requestData)

        // ✅ Circuit Breaker + Reactor Retry 조합 (타입 명시)
        val result: Map<String, Any>? = circuitBreaker.run(Supplier {
            webClient.post()
                .uri(apiUrl)
                .headers { headers ->
                    headers.contentType = MediaType.APPLICATION_JSON
                    headers.set("Accept", "application/json")
                    headers.set("User-Agent", "S-Class-Platform/1.0")

                    val auth = "${nicePayConfig.clientId}:${nicePayConfig.secretKey}"
                    val encodedAuth = Base64.getEncoder()
                        .encodeToString(auth.toByteArray(StandardCharsets.UTF_8))
                    headers.set("Authorization", "Basic $encodedAuth")
                }
                .bodyValue(requestData)
                .retrieve()
                .bodyToMono<Map<String, Any>>(
                    object : ParameterizedTypeReference<Map<String, Any>>() {}
                )
                .retryWhen(retrySpec) // ✅ Reactor Retry 적용
                .doOnError { error ->
                    log.error("나이스페이 승인 API 호출 최종 실패", error)
                }
                .block() // 동기 호출을 위해 block() 사용
        })

        if (result == null) {
            throw RuntimeException("NicePay 서비스가 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해주세요.")
        }

        validateResponse(result)
        log.info("나이스페이 승인 성공")
    }

    fun createPaymentInfo(
        payment: Payment,
        productTitle: String,
        userEmail: String,
        userNickname: String,
        returnUrl: String?
    ): Map<String, Any> {
        return mapOf(
            "orderId" to payment.pgOrderId,
            "amount" to payment.totalAmount,
            "currency" to "KRW",
            "goodsName" to productTitle,
            "customerEmail" to userEmail,
            "customerName" to userNickname,
            "returnUrl" to (returnUrl ?: nicePayConfig.returnUrl),
            "cancelUrl" to nicePayConfig.cancelUrl,
            "clientId" to nicePayConfig.clientId,
            "baseUrl" to nicePayConfig.baseUrl,
            "timestamp" to System.currentTimeMillis()
        )
    }
    private fun getTransactionId(tid: String?, orderId: String): String {
        return if (tid.isNullOrBlank()) {
            log.warn("tid가 전달되지 않음. orderId를 사용합니다: {}", orderId)
            orderId
        } else {
            tid
        }
    }

    private fun createRequestData(payment: Payment, authToken: String, amount: String): Map<String, Any> {
        return mapOf(
            "amount" to amount.toInt(),
            "orderId" to payment.pgOrderId,
            "authToken" to authToken
        )
    }

    private fun logRequestDetails(apiUrl: String, requestData: Map<String, Any>) {
        log.info("나이스페이 승인 API 요청 정보:")
        log.info("URL: {}", apiUrl)
        log.info("Request Data: {}", requestData)
        log.info("Client ID: {}", nicePayConfig.clientId)
        log.info("Secret Key: {}...", nicePayConfig.secretKey.take(8))
    }

    private fun validateResponse(response: Map<String, Any>?) {
        log.info("나이스페이 승인 API 응답: {}", response)

        if (response == null) {
            throw RuntimeException("나이스페이 승인 API 응답이 비어있습니다")
        }

        val resultCode = response["resultCode"] as? String ?: return

        if (resultCode != "0000") {
            val resultMsg = response["resultMsg"] as? String ?: "알 수 없는 오류"
            throw RuntimeException("나이스페이 승인 실패 - resultCode: $resultCode, resultMsg: $resultMsg")
        }
    }
}