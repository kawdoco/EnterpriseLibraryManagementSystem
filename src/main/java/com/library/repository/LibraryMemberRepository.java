package com.library.repository;

import com.library.entity.LibraryMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface LibraryMemberRepository extends JpaRepository<LibraryMember, Long> {

    Optional<LibraryMember> findByRfidCardUid(String rfidCardUid);

    boolean existsByMemberCode(String memberCode);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByRfidCardUid(String rfidCardUid);

    boolean existsByRfidCardUidAndIdNot(String rfidCardUid, Long id);

    boolean existsByMemberCodeAndIdNot(String memberCode, Long id);

    long countByBlacklistedTrue();

    Page<LibraryMember> findByBlacklistedTrue(Pageable pageable);

    /** Row lock used while changing blacklist state or checking limits during borrow. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from LibraryMember m where m.id = :id")
    Optional<LibraryMember> findByIdForUpdate(@Param("id") Long id);

    /** keyword must be non-null; pass "" for "no filter". */
    @Query("""
            select m from LibraryMember m
            where lower(m.fullName) like lower(concat('%', :kw, '%'))
               or lower(m.memberCode) like lower(concat('%', :kw, '%'))
               or lower(coalesce(m.email, '')) like lower(concat('%', :kw, '%'))
               or lower(coalesce(m.phone, '')) like lower(concat('%', :kw, '%'))
            """)
    Page<LibraryMember> search(@Param("kw") String keyword, Pageable pageable);
}
