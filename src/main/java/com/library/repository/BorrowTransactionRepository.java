package com.library.repository;

import com.library.entity.BorrowTransaction;
import com.library.model.TransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BorrowTransactionRepository extends JpaRepository<BorrowTransaction, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from BorrowTransaction t where t.book.id = :bookId and t.status = com.library.model.TransactionStatus.BORROWED")
    Optional<BorrowTransaction> findActiveByBookIdForUpdate(@Param("bookId") Long bookId);

    long countByMemberIdAndStatus(Long memberId, TransactionStatus status);

    long countByStatus(TransactionStatus status);

    /** Active loans of one member with the book loaded (safe to render with open-in-view=false). */
    @Query("""
            select t from BorrowTransaction t join fetch t.book
            where t.member.id = :memberId and t.status = com.library.model.TransactionStatus.BORROWED
            order by t.dueDate asc
            """)
    List<BorrowTransaction> findActiveByMember(@Param("memberId") Long memberId);

    @Query("select t from BorrowTransaction t join fetch t.member join fetch t.book where t.id = :id")
    Optional<BorrowTransaction> findWithDetailsById(@Param("id") Long id);

    // ---------- listings (keyword must be non-null) ----------

    @Query(value = """
            select t from BorrowTransaction t join fetch t.member m join fetch t.book b
            where lower(m.fullName) like lower(concat('%', :kw, '%'))
               or lower(m.memberCode) like lower(concat('%', :kw, '%'))
               or lower(b.title) like lower(concat('%', :kw, '%'))
            order by t.createdAt desc
            """,
            countQuery = """
            select count(t) from BorrowTransaction t join t.member m join t.book b
            where lower(m.fullName) like lower(concat('%', :kw, '%'))
               or lower(m.memberCode) like lower(concat('%', :kw, '%'))
               or lower(b.title) like lower(concat('%', :kw, '%'))
            """)
    Page<BorrowTransaction> searchAll(@Param("kw") String keyword, Pageable pageable);

    @Query(value = """
            select t from BorrowTransaction t join fetch t.member m join fetch t.book b
            where t.status = :status
              and (lower(m.fullName) like lower(concat('%', :kw, '%'))
               or lower(m.memberCode) like lower(concat('%', :kw, '%'))
               or lower(b.title) like lower(concat('%', :kw, '%')))
            order by t.createdAt desc
            """,
            countQuery = """
            select count(t) from BorrowTransaction t join t.member m join t.book b
            where t.status = :status
              and (lower(m.fullName) like lower(concat('%', :kw, '%'))
               or lower(m.memberCode) like lower(concat('%', :kw, '%'))
               or lower(b.title) like lower(concat('%', :kw, '%')))
            """)
    Page<BorrowTransaction> searchByStatus(@Param("kw") String keyword,
                                           @Param("status") TransactionStatus status,
                                           Pageable pageable);

    /** Active loans whose due date is strictly before today. */
    @Query(value = """
            select t from BorrowTransaction t join fetch t.member join fetch t.book
            where t.status = com.library.model.TransactionStatus.BORROWED and t.dueDate < :today
            order by t.dueDate asc
            """,
            countQuery = """
            select count(t) from BorrowTransaction t
            where t.status = com.library.model.TransactionStatus.BORROWED and t.dueDate < :today
            """)
    Page<BorrowTransaction> findOverdue(@Param("today") LocalDate today, Pageable pageable);

    @Query("select count(t) from BorrowTransaction t where t.status = com.library.model.TransactionStatus.BORROWED and t.dueDate < :today")
    long countOverdue(@Param("today") LocalDate today);

    // ---------- reminders ----------

    @Query("""
            select t from BorrowTransaction t join fetch t.member join fetch t.book
            where t.status = com.library.model.TransactionStatus.BORROWED and t.dueDate = :date
            """)
    List<BorrowTransaction> findActiveDueOn(@Param("date") LocalDate date);

    @Query("""
            select t from BorrowTransaction t join fetch t.member join fetch t.book
            where t.status = com.library.model.TransactionStatus.BORROWED and t.dueDate < :today
            """)
    List<BorrowTransaction> findActiveOverdue(@Param("today") LocalDate today);

    // ---------- dashboard / fines ----------

    /** Most recent borrow-or-return activity first. */
    @Query(value = """
            select t from BorrowTransaction t join fetch t.member join fetch t.book
            order by coalesce(t.returnedAt, t.borrowedAt) desc
            """,
            countQuery = "select count(t) from BorrowTransaction t")
    Page<BorrowTransaction> findRecentActivity(Pageable pageable);

    @Query("select coalesce(sum(t.fineAmount), 0) from BorrowTransaction t")
    BigDecimal sumAssessedFines();

    @Query("select coalesce(sum(t.fineAmount), 0) from BorrowTransaction t where t.member.id = :memberId")
    BigDecimal sumAssessedFinesByMember(@Param("memberId") Long memberId);

    /** Transactions that still owe money: assessed fine greater than the sum of their payments. */
    @Query(value = """
            select t from BorrowTransaction t join fetch t.member join fetch t.book
            where t.fineAmount > coalesce((select sum(p.amount) from FinePayment p where p.transaction = t), 0)
            order by t.createdAt desc
            """,
            countQuery = """
            select count(t) from BorrowTransaction t
            where t.fineAmount > coalesce((select sum(p.amount) from FinePayment p where p.transaction = t), 0)
            """)
    Page<BorrowTransaction> findWithOutstandingFine(Pageable pageable);
}
