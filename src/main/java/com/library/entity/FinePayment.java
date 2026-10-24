package com.library.entity;

import com.library.model.PaymentMethod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Money actually collected. Collected-fines reports sum THIS table only. */
@Getter
@Setter
@Entity
@Table(name = "fine_payments",
        indexes = @Index(name = "idx_payment_tx", columnList = "transaction_id"))
public class FinePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private BorrowTransaction transaction;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "recorded_by", length = 100)
    private String recordedBy;
}
