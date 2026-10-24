package com.library.entity;

import com.library.model.ReminderType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Deduplication: at most one reminder of each type per transaction per day. */
@Getter
@Setter
@Entity
@Table(name = "reminder_logs",
        uniqueConstraints = @UniqueConstraint(name = "uk_reminder_once",
                columnNames = {"transaction_id", "reminder_type", "reminder_date"}))
public class ReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private BorrowTransaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, length = 20)
    private ReminderType reminderType;

    @Column(name = "reminder_date", nullable = false)
    private LocalDate reminderDate;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
}
