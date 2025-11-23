package com.sclass.payment.client

import com.sclass.payment.dto.ProductDto
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@FeignClient(name = "product-service", path = "/api/products")
interface ProductServiceClient {

    @GetMapping("/{productId}")
    fun getProduct(@PathVariable productId: Long): ProductDto?
}