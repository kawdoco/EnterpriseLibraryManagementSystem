package com.library.service;

import com.library.dto.OutstandingFineRow;
import com.library.dto.OverdueRow;
import com.library.entity.BorrowTransaction;
import com.library.entity.FinePayment;
import com.library.exception.LibraryException;
import com.library.model.TransactionStatus;
import com.library.repository.BorrowTransactionRepository;
import com.library.repository.FinePaymentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Read-side queries for transaction and fine pages. Transactions are never deleted. */
@Service
@Transactional(readOnly = true)
public class TransactionService {

    private final BorrowTransactionRepository txRepo;
    private final FinePaymentRepository paymentRepo;
    private final FineService fineService;

    public TransactionService(BorrowTransactionRepository txRepo, FinePaymentRepository paymentRepo,
                              FineService fineService) {
        this.txRepo = txRepo;
        this.paymentRepo = paymentRepo;
        this.fineService = fineService;
    }

    /** status == null means all statuses. */
    public Page<BorrowTransaction> list(String keyword, TransactionStatus status, Pageable pageable) {
        String kw = keyword == null ? "" : keyword.trim();
        return status == null ? txRepo.searchAll(kw, pageable) : txRepo.searchByStatus(kw, status, pageable);
    }

    public Page<OverdueRow> overdue(Pageable pageable) {
        LocalDate today = LocalDate.now();
        return txRepo.findOverdue(today, pageable).map(t -> new OverdueRow(t,
                fineService.overdueDays(t.getDueDate(), today),
                fineService.calculateFine(t.getDueDate(), today)));
    }

    public List<BorrowTransaction> activeLoans(Long memberId) {
        return txRepo.findActiveByMember(memberId);
    }

    public BorrowTransaction getWithDetails(Long id) {
        return txRepo.findWithDetailsById(id).orElseThrow(() -> LibraryException.notFound("Transaction"));
    }

    public Page<OutstandingFineRow> outstandingFines(Pageable pageable) {
        return txRepo.findWithOutstandingFine(pageable).map(t -> {
            BigDecimal paid = paymentRepo.sumByTransaction(t.getId());
            return new OutstandingFineRow(t, t.getFineAmount(), paid, fineService.outstandingForTransaction(t.getId()));
        });
    }

    public Page<FinePayment> payments(Pageable pageable) {
        return paymentRepo.findAllRecent(pageable);
    }
}
