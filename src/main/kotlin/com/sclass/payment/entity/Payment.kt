package com.sclass.payment.entity

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener::class)
class Payment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    // 🎯 외부 서비스 참조 (마이크로서비스 분리)
    @Column(nullable = false, length = 100)
    var orderId: String = "",

    @Column(nullable = false)
    var userId: String = "",

    @Column(nullable = false)
    var productId: Long = 0,

    @Column(nullable = false)
    var totalAmount: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentStatus = PaymentStatus.PENDING,

    @Column(nullable = false, length = 20)
    var pgType: String = "",

    @Column(nullable = false, length = 100, unique = true)
    var pgOrderId: String = "",

    @Column(length = 100)
    var pgTid: String? = null,

    @Column(length = 50)
    var paymentMethod: String? = null,

    @Column(length = 500)
    var authToken: String? = null,

    @Column(length = 100)
    var authResultCode: String? = null,

    @Column(length = 500)
    var memo: String? = null,

    @Column(length = 1000)
    var metadata: String? = null,

    @Column
    var approvedAt: LocalDateTime? = null,

    @Column
    var failedAt: LocalDateTime? = null,

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    enum class PaymentStatus {
        PENDING, SUCCEED, CANCELLED, FAILED, REFUNDED, PARTIAL_REFUNDED
    }

    /**
     * 결제 승인 처리
     * @param tid PG사 거래 ID
     * @param authToken PG사 인증 토큰
     * @param authResultCode PG사 인증 결과 코드
     */
    fun approve(tid: String, authToken: String, authResultCode: String) {
        require(status == PaymentStatus.PENDING) {
            "대기 중인 결제만 승인할 수 있습니다. 현재 상태: $status"
        }
        require(tid.isNotBlank()) {
            "거래 ID는 필수입니다"
        }

        this.pgTid = tid
        this.authToken = authToken
        this.authResultCode = authResultCode
        this.status = PaymentStatus.SUCCEED
        this.approvedAt = LocalDateTime.now()
    }

    /**
     * 결제 실패 처리
     * @param tid PG사 거래 ID (선택적, 실패 시에도 tid가 있을 수 있음)
     * @param reason 실패 사유
     */
    fun fail(tid: String? = null, reason: String? = null) {  // ✅ tid 파라미터 추가
        require(status == PaymentStatus.PENDING) {
            "대기 중인 결제만 실패 처리할 수 있습니다. 현재 상태: $status"
        }

        this.status = PaymentStatus.FAILED
        this.failedAt = LocalDateTime.now()

        // ✅ tid가 제공되면 설정
        if (tid != null) {
            this.pgTid = tid
        }

        if (reason != null) {
            this.memo = reason
        }
    }

    /**
     * 결제 취소 처리
     * @param reason 취소 사유
     */
    fun cancel(reason: String? = null) {
        require(status == PaymentStatus.PENDING) {
            "대기 중인 결제만 취소할 수 있습니다. 현재 상태: $status"
        }

        this.status = PaymentStatus.CANCELLED
        if (reason != null) {
            this.memo = reason
        }
    }

    // ✅ 상태 확인 헬퍼 메서드
    fun isPending(): Boolean = status == PaymentStatus.PENDING
    fun isApproved(): Boolean = status == PaymentStatus.SUCCEED
    fun isFailed(): Boolean = status == PaymentStatus.FAILED
    fun isCancelled(): Boolean = status == PaymentStatus.CANCELLED
    fun canBeCancelled(): Boolean = status == PaymentStatus.PENDING
    fun canBeRefunded(): Boolean = status == PaymentStatus.SUCCEED

    @PrePersist
    fun onCreate() {
        createdAt = LocalDateTime.now()
        updatedAt = LocalDateTime.now()
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }
}