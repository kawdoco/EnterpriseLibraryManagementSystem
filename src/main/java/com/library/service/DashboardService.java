package com.library.service;

import com.library.dto.DashboardStats;
import com.library.model.BookStatus;
import com.library.model.TransactionStatus;
import com.library.repository.BookRepository;
import com.library.repository.BorrowTransactionRepository;
import com.library.repository.LibraryMemberRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** Every figure comes from COUNT/SUM queries; no record list is loaded into memory. */
@Service
public class DashboardService {

    private final LibraryMemberRepository memberRepo;
    private final BookRepository bookRepo;
    private final BorrowTransactionRepository txRepo;
    private final FineService fineService;

    public DashboardService(LibraryMemberRepository memberRepo, BookRepository bookRepo,
                            BorrowTransactionRepository txRepo, FineService fineService) {
        this.memberRepo = memberRepo;
        this.bookRepo = bookRepo;
        this.txRepo = txRepo;
        this.fineService = fineService;
    }

    @Transactional(readOnly = true)
    public DashboardStats summary() {
        return new DashboardStats(
                memberRepo.count(),
                bookRepo.count(),
                bookRepo.countByStatus(BookStatus.AVAILABLE),
                bookRepo.countByStatus(BookStatus.BORROWED),
                txRepo.countOverdue(LocalDate.now()),
                txRepo.countByStatus(TransactionStatus.BORROWED),
                fineService.totalAssessed(),
                fineService.totalCollected(),
                fineService.totalOutstanding(),
                memberRepo.countByBlacklistedTrue(),
                bookRepo.findTop5ByOrderByBorrowCountDescIdAsc(),
                txRepo.findRecentActivity(PageRequest.of(0, 8)).getContent());
    }
}
