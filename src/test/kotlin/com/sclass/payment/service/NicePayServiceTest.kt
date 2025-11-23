package com.sclass.payment.service

import com.sclass.payment.config.NicePayConfig
import com.sclass.payment.entity.Payment
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString

import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.*
import org.springframework.core.ParameterizedTypeReference

import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec
import reactor.core.publisher.Mono

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NicePayService Unit Test")
class NicePayServiceTest {

 @Mock
 private lateinit var nicePayConfig: NicePayConfig

 @Mock
 private lateinit var webClient: WebClient

 @Mock
 private lateinit var requestBodyUriSpec: RequestBodyUriSpec

 @Mock
 private lateinit var requestBodySpec: RequestBodySpec

 @Mock
 private lateinit var responseSpec: ResponseSpec

 private lateinit var nicePayService: NicePayService

 private lateinit var testPayment: Payment

 @BeforeEach
 fun setUp() {
  nicePayService = NicePayService(nicePayConfig, webClient)
  testPayment = createTestPayment()
  setupNicePayConfig()
  setupWebClientMocks()
 }

 @Test
 @DisplayName("결제 승인 - 성공")
 fun `approvePayment should succeed when NicePay API returns success`() {
  // Given
  val tid = "test-tid-123"
  val authToken = "test-auth-token"
  val amount = "10000"

  val responseBody = mapOf(
   "resultCode" to "0000",
   "resultMsg" to "정상처리"
  )

  whenever(
   responseSpec.bodyToMono<Map<String, Any>>(
    any<ParameterizedTypeReference<Map<String, Any>>>()
   )
  ).thenReturn(Mono.just(responseBody))

  // When
  nicePayService.approvePayment(testPayment, tid, authToken, amount)

  // Then
  verify(webClient).post()
  verify(requestBodyUriSpec).uri(anyString())
  verify(requestBodySpec).headers(any())
  verify(requestBodySpec).bodyValue(any<Map<String, Any>>())
  verify(requestBodySpec).retrieve()
  verify(responseSpec).bodyToMono<Map<String, Any>>(any<ParameterizedTypeReference<Map<String, Any>>>())
 }

 @Test
 @DisplayName("결제 승인 - NicePay API 실패 (resultCode != 0000)")
 fun `approvePayment should throw exception when resultCode is not 0000`() {
  // Given
  val tid = "test-tid-123"
  val authToken = "test-auth-token"
  val amount = "10000"

  val responseBody = mapOf(
   "resultCode" to "1001",
   "resultMsg" to "처리실패"
  )

  whenever(
   responseSpec.bodyToMono<Map<String, Any>>(
    any<ParameterizedTypeReference<Map<String, Any>>>()
   )
  ).thenReturn(Mono.just(responseBody))

  // When & Then
  assertThatThrownBy {
   nicePayService.approvePayment(testPayment, tid, authToken, amount)
  }
   .isInstanceOf(RuntimeException::class.java)
   .hasMessageContaining("나이스페이 승인 실패")
   .hasMessageContaining("resultCode: 1001")
   .hasMessageContaining("resultMsg: 처리실패")
 }

 @Test
 @DisplayName("결제 승인 - API 응답이 null인 경우")
 fun `approvePayment should throw exception when response is null`() {
  // Given
  val tid = "test-tid-123"
  val authToken = "test-auth-token"
  val amount = "10000"

  whenever(
   responseSpec.bodyToMono<Map<String, Any>>(
    any<ParameterizedTypeReference<Map<String, Any>>>()
   )
  ).thenReturn(Mono.empty())

  // When & Then
  assertThatThrownBy {
   nicePayService.approvePayment(testPayment, tid, authToken, amount)
  }
   .isInstanceOf(RuntimeException::class.java)
   .hasMessageContaining("나이스페이 승인 API 응답이 비어있습니다")
 }

 @Test
 @DisplayName("결제 승인 - WebClient 예외 발생")
 fun `approvePayment should throw exception when WebClient throws exception`() {
  // Given
  val tid = "test-tid-123"
  val authToken = "test-auth-token"
  val amount = "10000"

  whenever(
   responseSpec.bodyToMono<Map<String, Any>>(
    any<ParameterizedTypeReference<Map<String, Any>>>()
   )
  ).thenReturn(Mono.error(RuntimeException("Network error")))

  // When & Then
  assertThatThrownBy {
   nicePayService.approvePayment(testPayment, tid, authToken, amount)
  }
   .isInstanceOf(RuntimeException::class.java)
   .hasMessage("Network error")
 }

 @Test
 @DisplayName("결제 승인 - tid가 null인 경우 orderId 사용")
 fun `approvePayment should use orderId when tid is null`() {
  // Given
  val tid: String? = null
  val authToken = "test-auth-token"
  val amount = "10000"
  val responseBody = mapOf("resultCode" to "0000")

  whenever(
   responseSpec.bodyToMono<Map<String, Any>>(
    any<ParameterizedTypeReference<Map<String, Any>>>()
   )
  ).thenReturn(Mono.just(responseBody))

  // When
  nicePayService.approvePayment(testPayment, tid ?: "", authToken, amount)

  verify(requestBodyUriSpec).uri(argThat<String> { uri ->
   uri.contains(testPayment.pgOrderId)
  })
 }

 @Test
 @DisplayName("결제 승인 - tid가 빈 문자열인 경우 orderId 사용")
 fun `approvePayment should use orderId when tid is blank`() {
  // Given
  val tid = ""
  val authToken = "test-auth-token"
  val amount = "10000"
  val responseBody = mapOf("resultCode" to "0000")

  whenever(
   responseSpec.bodyToMono<Map<String, Any>>(
    any<ParameterizedTypeReference<Map<String, Any>>>()
   )
  ).thenReturn(Mono.just(responseBody))

  // When
  nicePayService.approvePayment(testPayment, tid, authToken, amount)

  // Then
  // ✅ 명시적으로 String 타입을 지정
  verify(requestBodyUriSpec).uri(argThat<String> { uri ->
   uri.contains(testPayment.pgOrderId)
  })
 }

 @Test
 @DisplayName("결제 승인 - 요청 데이터 생성 확인")
 fun `approvePayment should create correct request data`() {
  // Given
  val tid = "test-tid-123"
  val authToken = "test-auth-token"
  val amount = "10000"
  val responseBody = mapOf("resultCode" to "0000")

  whenever(
   responseSpec.bodyToMono<Map<String, Any>>(
    any<ParameterizedTypeReference<Map<String, Any>>>()
   )
  ).thenReturn(Mono.just(responseBody))

  // When
  nicePayService.approvePayment(testPayment, tid, authToken, amount)

  // Then
  verify(requestBodySpec).bodyValue(argThat { data ->
   val map = data as? Map<*, *> ?: return@argThat false
   map.get("amount") == 10000 &&  // ✅ get() 메서드 사용
           map.get("orderId") == testPayment.pgOrderId &&
           map.get("authToken") == authToken
  })
 }

 @Test
 @DisplayName("결제 승인 - Authorization 헤더 생성 확인")
 fun `approvePayment should create correct Authorization header`() {
  // Given
  val tid = "test-tid-123"
  val authToken = "test-auth-token"
  val amount = "10000"
  val responseBody = mapOf("resultCode" to "0000")

  whenever(nicePayConfig.clientId).thenReturn("test-client-id")
  whenever(nicePayConfig.secretKey).thenReturn("test-secret-key")
  whenever(
   responseSpec.bodyToMono<Map<String, Any>>(
    any<ParameterizedTypeReference<Map<String, Any>>>()
   )
  ).thenReturn(Mono.just(responseBody))

  // When
  nicePayService.approvePayment(testPayment, tid, authToken, amount)

  verify(requestBodySpec).headers(argThat { consumer ->
   val headers = org.springframework.http.HttpHeaders()
   consumer.accept(headers)
   headers.getFirst("Authorization")?.startsWith("Basic ") == true
  })
 }

 @Test
 @DisplayName("결제 정보 생성 - 성공")
 fun `createPaymentInfo should return payment info map`() {
  // Given
  val productTitle = "테스트 상품"
  val userEmail = "test@example.com"
  val userNickname = "테스트사용자"
  val returnUrl = "http://localhost:3000/payment/result"

  setupNicePayConfig()

  // When
  val result = nicePayService.createPaymentInfo(
   testPayment,
   productTitle,
   userEmail,
   userNickname,
   returnUrl
  )

  // Then
  assertThat(result).isNotNull
  assertThat(result["orderId"]).isEqualTo(testPayment.pgOrderId)
  assertThat(result["amount"]).isEqualTo(testPayment.totalAmount)
  assertThat(result["currency"]).isEqualTo("KRW")
  assertThat(result["goodsName"]).isEqualTo(productTitle)
  assertThat(result["customerEmail"]).isEqualTo(userEmail)
  assertThat(result["customerName"]).isEqualTo(userNickname)
  assertThat(result["returnUrl"]).isEqualTo(returnUrl)
  assertThat(result["cancelUrl"]).isEqualTo("http://localhost:8080/api/payments/cancel")
  assertThat(result["clientId"]).isEqualTo("test-client-id")
  assertThat(result["baseUrl"]).isEqualTo("https://sandbox-api.nicepay.co.kr")
  assertThat(result["timestamp"]).isNotNull
  assertThat(result).doesNotContainKey("secretKey")
 }

 @Test
 @DisplayName("결제 정보 생성 - returnUrl이 null인 경우 기본값 사용")
 fun `createPaymentInfo should use default returnUrl when returnUrl is null`() {
  // Given
  val productTitle = "테스트 상품"
  val userEmail = "test@example.com"
  val userNickname = "테스트사용자"
  val returnUrl: String? = null

  setupNicePayConfig()

  // When
  val result = nicePayService.createPaymentInfo(
   testPayment,
   productTitle,
   userEmail,
   userNickname,
   returnUrl
  )

  // Then
  assertThat(result["returnUrl"]).isEqualTo("http://localhost:8080/api/payments/return")
 }

 @Test
 @DisplayName("결제 정보 생성 - 모든 필수 필드 포함 확인")
 fun `createPaymentInfo should include all required fields`() {
  // Given
  val productTitle = "테스트 상품"
  val userEmail = "test@example.com"
  val userNickname = "테스트사용자"
  val returnUrl = "http://localhost:3000/payment/result"

  setupNicePayConfig()

  // When
  val result = nicePayService.createPaymentInfo(
   testPayment,
   productTitle,
   userEmail,
   userNickname,
   returnUrl
  )

  // Then
  val requiredFields = listOf(
   "orderId", "amount", "currency", "goodsName",
   "customerEmail", "customerName", "returnUrl", "cancelUrl",
   "clientId", "baseUrl", "timestamp"
  )
  requiredFields.forEach { field ->
   assertThat(result).containsKey(field)
   assertThat(result[field]).isNotNull
  }
 }

 // Helper 메서드들
 private fun setupNicePayConfig() {
  whenever(nicePayConfig.baseUrl)
   .thenReturn("https://sandbox-api.nicepay.co.kr")
  whenever(nicePayConfig.clientId)
   .thenReturn("test-client-id")
  whenever(nicePayConfig.secretKey)
   .thenReturn("test-secret-key")
  whenever(nicePayConfig.returnUrl)
   .thenReturn("http://localhost:8080/api/payments/return")
  whenever(nicePayConfig.cancelUrl)
   .thenReturn("http://localhost:8080/api/payments/cancel")
 }

 private fun setupWebClientMocks() {
  whenever(webClient.post())
   .thenReturn(requestBodyUriSpec)
  doReturn(requestBodySpec)
   .whenever(requestBodyUriSpec)
   .uri(anyString())

  whenever(requestBodySpec.headers(any()))
   .thenReturn(requestBodySpec)
  whenever(requestBodySpec.bodyValue(any<Any>()))
   .thenReturn(requestBodySpec)
  whenever(requestBodySpec.retrieve())
   .thenReturn(responseSpec)
 }
 private fun createTestPayment() = Payment(
  id = 1L,
  orderId = "order-123",
  userId = "test-user-id",
  productId = 1L,
  totalAmount = 10000,
  status = Payment.PaymentStatus.PENDING,
  pgType = "NICEPAY",
  pgOrderId = "NICE_1234567890_abcd1234"
 )
}