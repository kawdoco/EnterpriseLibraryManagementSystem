package com.library.repository;

import com.library.entity.FinePayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface FinePaymentRepository extends JpaRepository<FinePayment, Long> {

    @Query("select coalesce(sum(p.amount), 0) from FinePayment p")
    BigDecimal sumCollected();

    @Query("select coalesce(sum(p.amount), 0) from FinePayment p where p.transaction.id = :txId")
    BigDecimal sumByTransaction(@Param("txId") Long transactionId);

    @Query("select coalesce(sum(p.amount), 0) from FinePayment p where p.transaction.member.id = :memberId")
    BigDecimal sumByMember(@Param("memberId") Long memberId);

    @Query(value = """
            select p from FinePayment p join fetch p.transaction t join fetch t.member join fetch t.book
            order by p.paidAt desc
            """,
            countQuery = "select count(p) from FinePayment p")
    Page<FinePayment> findAllRecent(Pageable pageable);
}
