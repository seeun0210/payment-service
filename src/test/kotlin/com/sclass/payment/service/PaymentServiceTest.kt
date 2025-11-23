package com.sclass.payment.service

import com.sclass.payment.client.OrderServiceClient
import com.sclass.payment.client.ProductServiceClient
import com.sclass.payment.client.UserServiceClient
import com.sclass.payment.dto.PaymentCreateRequest
import com.sclass.payment.dto.ProductDto
import com.sclass.payment.dto.UserDto
import com.sclass.payment.entity.Payment
import com.sclass.payment.repository.PaymentRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.*
import org.mockito.quality.Strictness
import java.math.BigDecimal

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentService Unit Test")
class PaymentServiceTest {

 @Mock
 private lateinit var paymentRepository: PaymentRepository

 @Mock
 private lateinit var nicePayService: NicePayService

 @Mock
 private lateinit var productServiceClient: ProductServiceClient

 @Mock
 private lateinit var userServiceClient: UserServiceClient

 @Mock
 private lateinit var orderServiceClient: OrderServiceClient

 private lateinit var paymentService: PaymentService

 private lateinit var testUser: UserDto
 private lateinit var testProduct: ProductDto
 private lateinit var testRequest: PaymentCreateRequest

 @BeforeEach
 fun setUp() {
  testUser = createTestUser()
  testProduct = createTestProduct()
  testRequest = createTestPaymentRequest()

  paymentService = PaymentService(
   paymentRepository,
   nicePayService,
   productServiceClient,
   userServiceClient,
   orderServiceClient
  )

  whenever(paymentRepository.save(any<Payment>()))
   .thenAnswer { it.arguments[0] as Payment }
 }

 @Test
 @DisplayName("결제 준비 - 성공 (NICEPAY)")
 fun `preparePayment should return payment info for NICEPAY`() {
  // Given
  val userId = "test-user-id"
  val savedPayment = createTestPayment()
  val orderId = "order-123"

  whenever(productServiceClient.getProduct(testRequest.productId))
   .thenReturn(testProduct)
  whenever(userServiceClient.getUser(userId))
   .thenReturn(testUser)
  whenever(paymentRepository.save(any<Payment>()))
   .thenReturn(savedPayment)
  whenever(orderServiceClient.createOrder(any()))
   .thenReturn(orderId)
  whenever(nicePayService.createPaymentInfo(
   eq(savedPayment),
   eq("테스트 상품"),
   eq("test@example.com"),
   eq("테스트사용자"),
   eq(testRequest.returnUrl)
  )).thenReturn(createPaymentInfoMap(savedPayment))

  // When
  val result = paymentService.preparePayment(testRequest, userId)

  // Then
  assertThat(result).isNotNull
  assertThat(result["orderId"]).isEqualTo(savedPayment.pgOrderId)
  assertThat(result["amount"]).isEqualTo(10000)
  assertThat(result["currency"]).isEqualTo("KRW")
  assertThat(result["goodsName"]).isEqualTo("테스트 상품")
  assertThat(result["customerEmail"]).isEqualTo("test@example.com")
  assertThat(result["customerName"]).isEqualTo("테스트사용자")
  assertThat(result["clientId"]).isNotNull
  assertThat(result["baseUrl"]).isNotNull
  assertThat(result).doesNotContainKey("secretKey")

  verify(productServiceClient).getProduct(testRequest.productId)
  verify(userServiceClient).getUser(userId)
  verify(paymentRepository, atLeastOnce()).save(any<Payment>())
  verify(orderServiceClient).createOrder(any())
  verify(nicePayService).createPaymentInfo(
   eq(savedPayment),
   eq("테스트 상품"),
   eq("test@example.com"),
   eq("테스트사용자"),
   eq(testRequest.returnUrl)
  )
 }

 @Test
 @DisplayName("결제 준비 - 상품을 찾을 수 없음")
 fun `preparePayment should throw exception when product not found`() {
  // Given
  val userId = "test-user-id"

  whenever(productServiceClient.getProduct(testRequest.productId))
   .thenReturn(null)

  // When & Then
  assertThatThrownBy {
   paymentService.preparePayment(testRequest, userId)
  }
   .isInstanceOf(IllegalArgumentException::class.java)
   .hasMessage("상품을 찾을 수 없습니다")

  verify(productServiceClient).getProduct(testRequest.productId)
  verify(userServiceClient, never()).getUser(any())
  verify(paymentRepository, never()).save(any<Payment>())
 }

 @Test
 @DisplayName("결제 준비 - 사용자를 찾을 수 없음")
 fun `preparePayment should throw exception when user not found`() {
  // Given
  val userId = "nonexistent-user-id"

  whenever(productServiceClient.getProduct(testRequest.productId))
   .thenReturn(testProduct)
  whenever(userServiceClient.getUser(userId))
   .thenReturn(null)

  // When & Then
  assertThatThrownBy {
   paymentService.preparePayment(testRequest, userId)
  }
   .isInstanceOf(IllegalArgumentException::class.java)
   .hasMessage("사용자를 찾을 수 없습니다")

  verify(productServiceClient).getProduct(testRequest.productId)
  verify(userServiceClient).getUser(userId)
  verify(paymentRepository, never()).save(any<Payment>())
 }

 @Test
 @DisplayName("결제 승인 - 성공")
 fun `approvePayment should update payment status to SUCCEED`() {
  // Given
  val orderId = "NICE_1234567890_abcd1234"
  val tid = "test-tid-123"
  val authToken = "test-auth-token"
  val amount = "10000"
  val payment = createTestPayment()

  whenever(paymentRepository.findByPgOrderId(orderId))
   .thenReturn(payment)
  // ✅ save()가 전달된 Payment 객체를 그대로 반환하도록 설정
  whenever(paymentRepository.save(any<Payment>()))
   .thenAnswer { it.arguments[0] as Payment }
  doNothing().whenever(nicePayService).approvePayment(any(), any(), any(), any())
  doNothing().whenever(orderServiceClient).updateOrderStatus(any(), any())

  // When
  paymentService.approvePayment(orderId, tid, authToken, amount)

  // Then
  assertThat(payment.status).isEqualTo(Payment.PaymentStatus.SUCCEED)
  assertThat(payment.pgTid).isEqualTo(tid)
  assertThat(payment.authToken).isEqualTo(authToken)

  verify(paymentRepository).findByPgOrderId(orderId)
  verify(nicePayService).approvePayment(payment, tid, authToken, amount)
  verify(paymentRepository).save(payment)
  verify(orderServiceClient).updateOrderStatus(payment.orderId, "SUCCEED")
 }

 @Test
 @DisplayName("결제 승인 - 결제를 찾을 수 없음")
 fun `approvePayment should throw exception when payment not found`() {
  // Given
  val orderId = "NONEXISTENT_ORDER"
  val tid = "test-tid-123"
  val authToken = "test-auth-token"
  val amount = "10000"

  whenever(paymentRepository.findByPgOrderId(orderId))
   .thenReturn(null)

  // When & Then
  assertThatThrownBy {
   paymentService.approvePayment(orderId, tid, authToken, amount)
  }
   .isInstanceOf(IllegalArgumentException::class.java)
   .hasMessage("결제를 찾을 수 없습니다.")

  verify(paymentRepository).findByPgOrderId(orderId)
  verify(nicePayService, never()).approvePayment(any(), any(), any(), any())
  verify(paymentRepository, never()).save(any<Payment>())
 }

 @Test
 @DisplayName("결제 승인 - NicePayService 예외 발생 시 실패 처리")
 fun `approvePayment should update payment status to FAILED when NicePayService throws exception`() {
  // Given
  val orderId = "NICE_1234567890_abcd1234"
  val tid = "test-tid-123"
  val authToken = "test-auth-token"
  val amount = "10000"
  val payment = createTestPayment()

  whenever(paymentRepository.findByPgOrderId(orderId))
   .thenReturn(payment)

  // ✅ save()가 전달된 Payment 객체를 그대로 반환하도록 설정
  // 이렇게 하면 Payment 객체의 상태 변경이 유지됨
  whenever(paymentRepository.save(any<Payment>()))
   .thenAnswer {
    val savedPayment = it.arguments[0] as Payment
    // ✅ save() 호출 시점에 Payment 객체의 상태를 확인
    // 실제 PaymentService 코드에서 catch 블록에서 pgTid를 설정하고
    // finally 블록에서 save()를 호출하므로, save() 호출 시점에 이미 pgTid가 설정되어 있어야 함
    savedPayment
   }

  doThrow(RuntimeException("NicePay API 오류"))
   .whenever(nicePayService)
   .approvePayment(any(), any(), any(), any())
  doNothing().whenever(orderServiceClient).updateOrderStatus(any(), any())

  // When & Then
  assertThatThrownBy {
   paymentService.approvePayment(orderId, tid, authToken, amount)
  }
   .isInstanceOf(RuntimeException::class.java)
   .hasMessage("NicePay API 오류")

  // ✅ save() 호출 후에 Payment 객체의 상태를 확인
  verify(paymentRepository).save(any<Payment>())

  // ✅ 원본 Payment 객체의 상태 확인
  // 실제 PaymentService 코드에서 catch 블록에서 payment.pgTid = tid를 설정하고
  // finally 블록에서 save()를 호출하므로, save() 호출 후에 pgTid가 설정되어 있어야 함
  assertThat(payment.status).isEqualTo(Payment.PaymentStatus.FAILED)
  assertThat(payment.pgTid).isEqualTo(tid) // ✅ 이제 null이 아닌 값이어야 함

  verify(paymentRepository).findByPgOrderId(orderId)
  verify(nicePayService).approvePayment(payment, tid, authToken, amount)
  verify(orderServiceClient).updateOrderStatus(payment.orderId, "FAILED")
 }

 // Helper 메서드들
 private fun createTestUser() = UserDto(
  id = "test-user-id",
  email = "test@example.com",
  nickname = "테스트사용자"
 )

 private fun createTestProduct() = ProductDto(
  id = 1L,
  title = "테스트 상품",
  price = BigDecimal.valueOf(10000),
  category = "테스트 카테고리",
  author = "테스트 저자"
 )

 private fun createTestPaymentRequest() = PaymentCreateRequest(
  productId = 1L,
  pgType = "NICEPAY",
  returnUrl = "http://localhost:3000/payment/result",
  memo = "테스트 결제",
  metadata = "{\"test\": \"data\"}"
 )

 private fun createTestPayment() = Payment(
  id = 1L,
  orderId = "order-123",
  userId = "test-user-id",
  productId = 1L,
  totalAmount = 10000,
  status = Payment.PaymentStatus.PENDING,
  pgType = "NICEPAY",
  pgOrderId = "NICE_${System.currentTimeMillis()}_abcd1234"
 )

 private fun createPaymentInfoMap(payment: Payment): Map<String, Any> {
  return mapOf(
   "orderId" to payment.pgOrderId,
   "amount" to payment.totalAmount,
   "currency" to "KRW",
   "goodsName" to "테스트 상품",
   "customerEmail" to "test@example.com",
   "customerName" to "테스트사용자",
   "returnUrl" to "http://localhost:3000/payment/result",
   "cancelUrl" to "http://localhost:8080/api/payments/cancel",
   "clientId" to "test-client-id",
   "baseUrl" to "https://sandbox-api.nicepay.co.kr",
   "timestamp" to System.currentTimeMillis()
  )
 }
}