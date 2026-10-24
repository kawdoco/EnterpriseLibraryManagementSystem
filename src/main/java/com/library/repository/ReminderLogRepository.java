package com.library.repository;

import com.library.entity.ReminderLog;
import com.library.model.ReminderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface ReminderLogRepository extends JpaRepository<ReminderLog, Long> {
    boolean existsByTransactionIdAndReminderTypeAndReminderDate(Long transactionId, ReminderType type, LocalDate date);
}
