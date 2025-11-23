package com.sclass.payment.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.sclass.common.response.ApiResponse
import com.sclass.payment.dto.PaymentCreateRequest
import com.sclass.payment.service.PaymentService
import io.swagger.v3.oas.annotations.tags.Tag

import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/api/payments")
@Tag(name = "payments")
class PaymentController(
    private val paymentService: PaymentService,
    private val objectMapper: ObjectMapper,
    @Value("\${frontend.url}") private val frontendUrl: String
) {

    @PostMapping("/webhook/{type}")
    fun webhook(
        @PathVariable type: String,
        @RequestBody body: Map<String, Any>
    ): ResponseEntity<String> {
        // 🎯 Kotlin 장점: Expression body
        return ResponseEntity.ok("OK")
    }

    // 🎯 Kotlin 장점: Default parameters & Elvis operator
    @GetMapping("/cancel")
    fun handlePaymentCancelGet(
        @RequestParam(required = false) orderId: String?,
        @RequestParam(required = false) message: String?
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        val response = mapOf(
            "success" to false,
            "status" to "cancelled",
            "message" to (message ?: "결제가 취소되었습니다."),
            "orderId" to (orderId ?: "")
        )
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    // 🎯 Kotlin 장점: String templates & when expression
    @PostMapping("/return")
    fun handlePaymentReturn(
        @RequestParam(required = false) orderId: String?,
        @RequestParam(required = false) tid: String?,
        @RequestParam(required = false) authToken: String?,
        @RequestParam(required = false) authResultCode: String?,
        @RequestParam(required = false) amount: String?,
        response: HttpServletResponse
    ) {
        val paymentResult = if (authResultCode == "0000" && tid != null && authToken != null) {
            try {
                paymentService.approvePayment(orderId!!, tid, authToken, amount!!)
                mapOf(
                    "success" to true,
                    "status" to "success",
                    "message" to "결제가 완료되었습니다.",
                    "orderId" to orderId,
                    "tid" to tid
                )
            } catch (e: Exception) {
                mapOf(
                    "success" to false,
                    "status" to "failed",
                    "message" to "결제 승인 중 오류가 발생했습니다: ${e.message}",
                    "orderId" to orderId,
                    "tid" to tid
                )
            }
        } else {
            mapOf(
                "success" to false,
                "status" to "failed",
                "message" to "결제 인증에 실패했습니다.",
                "orderId" to orderId,
                "authResultCode" to authResultCode
            )
        }

        // 🎯 Kotlin 장점: String templates
        val encodedResult = URLEncoder.encode(
            objectMapper.writeValueAsString(paymentResult),
            StandardCharsets.UTF_8.toString()
        )

        response.sendRedirect("$frontendUrl/payment/result?data=$encodedResult")
    }

    @PostMapping("/cancel")
    fun handlePaymentCancel(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) orderId: String?,
        @RequestParam(required = false) message: String?,
        @RequestBody(required = false) body: Map<String, Any>?
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        val response = mapOf(
            "success" to false,
            "status" to "cancelled",
            "message" to (message ?: "결제가 취소되었습니다."),
            "orderId" to (orderId ?: "")
        )
        return ResponseEntity.ok(ApiResponse.success(response))
    }

//    @PostMapping("/prepare")
//    fun preparePayment(
//        @RequestBody request: PaymentCreateRequest,
//        @RequestUser user: RequestUserInfo
//    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
//        val paymentInfo = paymentService.preparePayment(request, user.userId)
//        return ResponseEntity.ok(ApiResponse.success(paymentInfo))
//    }
}