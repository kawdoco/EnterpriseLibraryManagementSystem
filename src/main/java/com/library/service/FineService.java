package com.library.service;

import com.library.dto.PaymentForm;
import com.library.dto.PaymentResult;
import com.library.entity.LibraryMember;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface FineService {

    /** Calendar days late; zero if returned on or before the due date. */
    long overdueDays(LocalDate dueDate, LocalDate returnDate);

    /** fine = overdueDays x fineRatePerDay. */
    BigDecimal calculateFine(LocalDate dueDate, LocalDate returnDate);

    BigDecimal outstandingForMember(Long memberId);

    BigDecimal outstandingForTransaction(Long transactionId);

    BigDecimal totalAssessed();

    BigDecimal totalCollected();

    BigDecimal totalOutstanding();

    /**
     * Blacklists the member if outstanding fines are strictly greater than the threshold.
     * Never clears an existing blacklist. Caller must already hold the member row lock.
     * @return true if the member was newly blacklisted by this call
     */
    boolean reassessBlacklist(LibraryMember member, String actor);

    PaymentResult recordPayment(PaymentForm form, String actor);

    /** Administrative manual blacklist. */
    void blacklistMember(Long memberId, String actor, String reason);

    /**
     * Administrative clearance. Allowed when outstanding fines are at or below the threshold;
     * above the threshold only with an explicit override. A reason is mandatory and audited.
     */
    void clearBlacklist(Long memberId, String actor, String reason, boolean override);
}
