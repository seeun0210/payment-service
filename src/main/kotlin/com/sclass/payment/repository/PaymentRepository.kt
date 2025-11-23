package com.sclass.payment.repository

import com.sclass.payment.entity.Payment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentRepository: JpaRepository<Payment, Long> {
    fun findByPgOrderId(pgOrderId: String): Payment?
    fun findByOrderId(orderId: String): Payment?
    fun findByPgTid(pgTid: String): Payment?

    fun findByUserId(userId: String):List<Payment>
    fun findByStatus(status: Payment.PaymentStatus): List<Payment>

    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<Payment>
    fun findByUserIdAndStatusOrderByCreatedAtDesc(userId: String, status: Payment.PaymentStatus): List<Payment>
}