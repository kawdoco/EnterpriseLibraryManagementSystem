package com.library.service.impl;

import com.library.config.LibraryProperties;
import com.library.dto.PaymentForm;
import com.library.dto.PaymentResult;
import com.library.entity.BorrowTransaction;
import com.library.entity.FinePayment;
import com.library.entity.LibraryMember;
import com.library.exception.ErrorCode;
import com.library.exception.LibraryException;
import com.library.model.AuditAction;
import com.library.repository.BorrowTransactionRepository;
import com.library.repository.FinePaymentRepository;
import com.library.repository.LibraryMemberRepository;
import com.library.service.AuditService;
import com.library.service.FineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class FineServiceImpl implements FineService {

    private final LibraryProperties props;
    private final BorrowTransactionRepository txRepo;
    private final FinePaymentRepository paymentRepo;
    private final LibraryMemberRepository memberRepo;
    private final AuditService audit;

    public FineServiceImpl(LibraryProperties props,
                           BorrowTransactionRepository txRepo,
                           FinePaymentRepository paymentRepo,
                           LibraryMemberRepository memberRepo,
                           AuditService audit) {
        this.props = props;
        this.txRepo = txRepo;
        this.paymentRepo = paymentRepo;
        this.memberRepo = memberRepo;
        this.audit = audit;
    }

    // ---------- pure calculation ----------

    @Override
    public long overdueDays(LocalDate dueDate, LocalDate returnDate) {
        return Math.max(0L, ChronoUnit.DAYS.between(dueDate, returnDate));
    }

    @Override
    public BigDecimal calculateFine(LocalDate dueDate, LocalDate returnDate) {
        return props.getFineRatePerDay()
                .multiply(BigDecimal.valueOf(overdueDays(dueDate, returnDate)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ---------- balances ----------

    @Override
    @Transactional(readOnly = true)
    public BigDecimal outstandingForMember(Long memberId) {
        BigDecimal owed = txRepo.sumAssessedFinesByMember(memberId).subtract(paymentRepo.sumByMember(memberId));
        return owed.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal outstandingForTransaction(Long transactionId) {
        BigDecimal fine = txRepo.findById(transactionId)
                .orElseThrow(() -> LibraryException.notFound("Transaction"))
                .getFineAmount();
        return fine.subtract(paymentRepo.sumByTransaction(transactionId)).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal totalAssessed() {
        return txRepo.sumAssessedFines().setScale(2, RoundingMode.HALF_UP);
    }

    /** Sums actual recorded payments only. */
    @Override
    @Transactional(readOnly = true)
    public BigDecimal totalCollected() {
        return paymentRepo.sumCollected().setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal totalOutstanding() {
        return totalAssessed().subtract(totalCollected()).max(BigDecimal.ZERO);
    }

    // ---------- blacklist ----------

    @Override
    @Transactional
    public boolean reassessBlacklist(LibraryMember member, String actor) {
        if (member.isBlacklisted()) return false;
        BigDecimal outstanding = outstandingForMember(member.getId());
        if (outstanding.compareTo(props.getBlacklistThreshold()) > 0) {
            member.setBlacklisted(true);
            memberRepo.save(member);
            audit.record(AuditAction.MEMBER_BLACKLISTED, actor, "LibraryMember", member.getId(),
                    "Outstanding fines " + outstanding + " " + props.getCurrency()
                            + " exceed threshold " + props.getBlacklistThreshold());
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public void blacklistMember(Long memberId, String actor, String reason) {
        requireReason(reason);
        LibraryMember m = memberRepo.findByIdForUpdate(memberId)
                .orElseThrow(() -> LibraryException.notFound("Member"));
        if (m.isBlacklisted()) return;
        m.setBlacklisted(true);
        memberRepo.save(m);
        audit.record(AuditAction.MEMBER_BLACKLISTED, actor, "LibraryMember", m.getId(), "Manual: " + reason);
    }

    @Override
    @Transactional
    public void clearBlacklist(Long memberId, String actor, String reason, boolean override) {
        requireReason(reason);
        LibraryMember m = memberRepo.findByIdForUpdate(memberId)
                .orElseThrow(() -> LibraryException.notFound("Member"));
        if (!m.isBlacklisted()) return;
        BigDecimal outstanding = outstandingForMember(memberId);
        boolean overThreshold = outstanding.compareTo(props.getBlacklistThreshold()) > 0;
        if (overThreshold && !override) {
            throw new LibraryException(ErrorCode.CLEARANCE_DENIED,
                    "Outstanding fines (" + outstanding + " " + props.getCurrency()
                            + ") still exceed the threshold. Record payments or use an explicit override.");
        }
        m.setBlacklisted(false);
        memberRepo.save(m);
        audit.record(AuditAction.BLACKLIST_CLEARED, actor, "LibraryMember", m.getId(),
                (override && overThreshold ? "OVERRIDE; " : "") + "Outstanding " + outstanding + "; reason: " + reason);
    }

    // ---------- payments ----------

    @Override
    @Transactional
    public PaymentResult recordPayment(PaymentForm form, String actor) {
        BorrowTransaction tx = txRepo.findById(form.getTransactionId())
                .orElseThrow(() -> LibraryException.notFound("Transaction"));
        // Lock the member row so concurrent payments/returns for one member are serialised.
        LibraryMember member = memberRepo.findByIdForUpdate(tx.getMember().getId())
                .orElseThrow(() -> LibraryException.notFound("Member"));

        BigDecimal amount = form.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal txOutstanding = outstandingForTransaction(tx.getId());
        if (txOutstanding.signum() == 0) {
            throw new LibraryException(ErrorCode.INVALID_PAYMENT, "This transaction has no outstanding fine.");
        }
        if (amount.compareTo(txOutstanding) > 0) {
            throw new LibraryException(ErrorCode.INVALID_PAYMENT,
                    "Payment exceeds the outstanding amount of " + txOutstanding + " " + props.getCurrency() + ".");
        }

        FinePayment p = new FinePayment();
        p.setTransaction(tx);
        p.setAmount(amount);
        p.setPaidAt(LocalDateTime.now());
        p.setPaymentMethod(form.getPaymentMethod());
        p.setReferenceNumber(blankToNull(form.getReferenceNumber()));
        p.setRecordedBy(actor);
        paymentRepo.saveAndFlush(p);

        audit.record(AuditAction.PAYMENT_RECORDED, actor, "FinePayment", p.getId(),
                "Tx " + tx.getId() + ", amount " + amount + " " + props.getCurrency() + " via " + p.getPaymentMethod());

        BigDecimal memberOutstanding = outstandingForMember(member.getId());
        // Policy: payments never clear a blacklist automatically. Admin clears it explicitly.
        boolean eligible = member.isBlacklisted()
                && memberOutstanding.compareTo(props.getBlacklistThreshold()) <= 0;

        return new PaymentResult(p.getId(), tx.getId(), member.getId(), member.getFullName(),
                amount, txOutstanding.subtract(amount), memberOutstanding,
                member.isBlacklisted(), eligible);
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new LibraryException(ErrorCode.INVALID_STATE, "A reason is required for blacklist changes.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
