package com.sclass.payment.dto

import java.time.LocalDateTime

data class PaymentResponse(
    val id: Long,
    val orderId: String,
    val pgOrderId: String,
    val totalAmount: String,
    val status: String,
    val pgType: String,
    val createdAt: LocalDateTime
)
