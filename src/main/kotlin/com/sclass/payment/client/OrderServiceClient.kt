package com.sclass.payment.client

import com.sclass.payment.dto.CreateOrderRequest
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.*

@FeignClient(name="order-service", path ="/api/orders")
interface OrderServiceClient {

    @PostMapping()
    fun createOrder(@RequestBody requestBody: CreateOrderRequest):String

    @PutMapping("/{orderId}/status")
    fun updateOrderStatus(
        @PathVariable orderId: String,
        @RequestParam status: String
    )
}