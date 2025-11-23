package com.sclass.payment.service

import com.sclass.payment.client.OrderServiceClient
import com.sclass.payment.client.ProductServiceClient
import com.sclass.payment.client.UserServiceClient
import com.sclass.payment.dto.CreateOrderRequest
import com.sclass.payment.dto.PaymentCreateRequest
import com.sclass.payment.entity.Payment
import com.sclass.payment.repository.PaymentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val nicePayService: NicePayService,
    private val productServiceClient: ProductServiceClient,
    private val userServiceClient: UserServiceClient,
    private val orderServiceClient: OrderServiceClient
) {

    private fun generateOrderId(pgType: String): String {
        val prefix = when (pgType) {
            "NICEPAY" -> "NICE"
            "TOSS" -> "TOSS"
            "KAKAO_PAY" -> "KAKAO"
            else -> "ORDER"
        }
        return "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    }

    @Transactional
    fun preparePayment(request: PaymentCreateRequest, userId: String): Map<String, Any> {
        // 1. 외부 서비스에서 상품 조회
        val product = productServiceClient.getProduct(request.productId)
            ?: throw IllegalArgumentException("상품을 찾을 수 없습니다")

        // 2. 외부 서비스에서 사용자 조회
        val user = userServiceClient.getUser(userId)
            ?: throw IllegalArgumentException("사용자를 찾을 수 없습니다")

        // 3. Payment 생성
        val pgOrderId = generateOrderId(request.pgType)

        val payment = Payment(
            userId = userId,
            productId = request.productId,
            totalAmount = product.price.intValueExact(),
            pgType = request.pgType,
            pgOrderId = pgOrderId,
            memo = request.memo,
            metadata = request.metadata
        )

        val savedPayment = paymentRepository.save(payment)

        // 4. Order 서비스에 주문 생성 요청
        val orderId = orderServiceClient.createOrder(
            CreateOrderRequest(
                userId = userId,
                productId = request.productId,
                paymentId = savedPayment.id,
                totalAmount = savedPayment.totalAmount
            )
        )

        // 5. Payment에 orderId 업데이트
        savedPayment.orderId = orderId
        paymentRepository.save(savedPayment)

        // 6. 결제 정보 반환 (product와 user 변수를 사용)
        return createPaymentInfo(
            payment = savedPayment,
            productTitle = product.title,      // ✅ product 변수 사용
            userEmail = user.email,            // ✅ user 변수 사용
            userNickname = user.nickname,      // ✅ user 변수 사용
            returnUrl = request.returnUrl
        )
    }

    @Transactional
    fun approvePayment(orderId: String, tid: String, authToken: String, amount: String) {
        val payment = paymentRepository.findByPgOrderId(orderId)
            ?: throw IllegalArgumentException("결제를 찾을 수 없습니다.")

        try {
            nicePayService.approvePayment(payment, tid, authToken, amount)
            payment.approve(tid, authToken, "0000")
            paymentRepository.save(payment)
            orderServiceClient.updateOrderStatus(payment.orderId, "SUCCEED")
        } catch (e: Exception) {
            payment.fail("결제 승인 중 오류: ${e.message}")
            paymentRepository.save(payment)
            orderServiceClient.updateOrderStatus(payment.orderId, "FAILED")
            throw e
        }
    }

    private fun createPaymentInfo(
        payment: Payment,
        productTitle: String,
        userEmail: String,
        userNickname: String,
        returnUrl: String?
    ): Map<String, Any> {
        return when (payment.pgType) {
            "NICEPAY" -> nicePayService.createPaymentInfo(
                payment = payment,
                productTitle = productTitle,
                userEmail = userEmail,
                userNickname = userNickname,
                returnUrl = returnUrl
            )
            "TOSS" -> createTossPaymentInfo(payment, productTitle, returnUrl)
            "KAKAO_PAY" -> createKakaoPaymentInfo(payment, productTitle, returnUrl)
            else -> throw IllegalArgumentException("지원하지 않는 PG사입니다: ${payment.pgType}")
        }
    }

    private fun createTossPaymentInfo(
        payment: Payment,
        productTitle: String,
        returnUrl: String?
    ): Map<String, Any> {
        return mapOf(
            "orderId" to payment.pgOrderId,
            "amount" to payment.totalAmount,
            "productName" to productTitle,
            "returnUrl" to (returnUrl ?: "")
        )
    }

    private fun createKakaoPaymentInfo(
        payment: Payment,
        productTitle: String,
        returnUrl: String?
    ): Map<String, Any> {
        return mapOf(
            "orderId" to payment.pgOrderId,
            "amount" to payment.totalAmount,
            "productName" to productTitle,
            "returnUrl" to (returnUrl ?: "")
        )
    }
}