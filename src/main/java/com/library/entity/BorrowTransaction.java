package com.library.entity;

import com.library.model.TransactionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Safety net against two active loans for one book:
 * {@code activeBookId} equals book.id while status = BORROWED and is NULL once RETURNED.
 * MySQL allows many NULLs in a UNIQUE index, so this behaves like a partial unique index.
 * The service layer also takes a pessimistic row lock on the book before borrowing.
 */
@Getter
@Setter
@Entity
@Table(name = "borrow_transactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_active_book_loan", columnNames = "active_book_id"),
        indexes = {
                @Index(name = "idx_tx_status_due", columnList = "status, due_date"),
                @Index(name = "idx_tx_member", columnList = "member_id"),
                @Index(name = "idx_tx_book", columnList = "book_id")
        })
public class BorrowTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private LibraryMember member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "borrowed_at", nullable = false)
    private LocalDateTime borrowedAt;

    /** Date-only policy: a book is on time if returned on or before this calendar date. */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    /** Assessed (owed) fine, NOT money collected. Payments live in FinePayment. */
    @Column(name = "fine_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal fineAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status = TransactionStatus.BORROWED;

    @Column(name = "active_book_id")
    private Long activeBookId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        syncActiveMarker();
    }

    @PreUpdate
    void onUpdate() {
        syncActiveMarker();
    }

    private void syncActiveMarker() {
        activeBookId = (status == TransactionStatus.BORROWED && book != null) ? book.getId() : null;
    }
}
