package com.sclass.payment.dto

data class CreateOrderRequest(
    val userId: String,
    val productId: Long,
    val paymentId: Long,
    val totalAmount: Int
)
