package com.sclass.payment.dto

import jakarta.validation.constraints.NotNull

data class PaymentCreateRequest(
    @field:NotNull(message = "상품 ID는 필수입니다")
    val productId: Long,

    @field:NotNull(message = "PG사 타입은 필수입니다")
    val pgType: String,

    val returnUrl: String? = null,
    val memo: String? = null,
    val metadata: String? = null
)