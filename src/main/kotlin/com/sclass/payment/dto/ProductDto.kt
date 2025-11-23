package com.sclass.payment.dto

import java.math.BigDecimal

data class ProductDto(
    val id: Long,
    val title: String,
    val price: BigDecimal,
    val category: String? = null,
    val author: String? = null,
    val imageUrl: String? = null,
    val description: String? = null
)
