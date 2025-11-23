package com.sclass.payment.client

import com.sclass.payment.dto.UserDto
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@FeignClient(name = "user-service", path = "/api/users")
interface UserServiceClient {

    @GetMapping("/{userId}")
    fun getUser(@PathVariable userId: String): UserDto?
}