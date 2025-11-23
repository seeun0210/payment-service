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
    // Order 서비스의 Order ID를 참조 (직접 관계 없음)
    @Column(nullable = false, length = 100)
    var orderId: String = "",  // 외부 Order 서비스의 ID (UUID 또는 String)

    // 결제를 요청한 사용자 정보 (외부 User 서비스 참조)
    @Column(nullable = false)
    var userId: String = "",

    // 결제할 상품 정보 (외부 Product 서비스 참조)
    @Column(nullable = false)
    var productId: Long = 0,

    // 결제 금액 (Payment 서비스가 관리하는 정보)
    @Column(nullable = false)
    var totalAmount: Int = 0,

    // 🎯 결제 상태 (Payment 서비스가 관리)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentStatus = PaymentStatus.PENDING,

    // 🎯 PG사 정보 (Payment 서비스가 관리)
    @Column(nullable = false, length = 20)
    var pgType: String = "",  // NICEPAY, TOSS, KAKAO_PAY

    // 🎯 PG사 주문 ID (Payment 서비스가 생성/관리)
    @Column(nullable = false, length = 100, unique = true)
    var pgOrderId: String = "",  // PG사에 전달할 주문 ID

    // 🎯 PG사 거래 ID (Transaction ID) - PG사에서 반환
    @Column(length = 100)
    var pgTid: String? = null,  // PG사에서 반환하는 거래 ID

    // 결제 수단
    @Column(length = 50)
    var paymentMethod: String? = null,  // CARD, BANK_TRANSFER 등

    // 🎯 결제 승인 정보
    @Column(length = 500)
    var authToken: String? = null,  // PG사 인증 토큰

    @Column(length = 100)
    var authResultCode: String? = null,  // PG사 인증 결과 코드

    // 추가 정보
    @Column(length = 500)
    var memo: String? = null,

    @Column(length = 1000)
    var metadata: String? = null,  // JSON 형태의 추가 메타데이터

    // 🎯 결제 완료/실패 시간
    @Column
    var approvedAt: LocalDateTime? = null,  // 결제 승인 시간

    @Column
    var failedAt: LocalDateTime? = null,  // 결제 실패 시간

    // BaseEntity 필드들
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

    fun fail(reason: String? = null) {
        require(status == PaymentStatus.PENDING) {
            "대기 중인 결제만 실패 처리할 수 있습니다. 현재 상태: $status"
        }

        this.status = PaymentStatus.FAILED
        this.failedAt = LocalDateTime.now()
        if (reason != null) {
            this.memo = reason
        }
    }

    fun cancel(reason: String? = null) {
        require(status == PaymentStatus.PENDING) {
            "대기 중인 결제만 취소할 수 있습니다. 현재 상태: $status"
        }

        this.status = PaymentStatus.CANCELLED
        if (reason != null) {
            this.memo = reason
        }
    }

    // ✅ 좋음: 상태 확인 헬퍼 메서드
    fun isPending(): Boolean = status == PaymentStatus.PENDING
    fun isApproved(): Boolean = status == PaymentStatus.SUCCEED
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