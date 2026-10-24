package com.library.repository;

import com.library.entity.Book;
import com.library.model.BookStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    Optional<Book> findByRfidTagUid(String rfidTagUid);

    boolean existsByRfidTagUid(String rfidTagUid);

    boolean existsByRfidTagUidAndIdNot(String rfidTagUid, Long id);

    long countByStatus(BookStatus status);

    /** Top five most-borrowed copies (ties broken by id for a stable order). */
    List<Book> findTop5ByOrderByBorrowCountDescIdAsc();

    /** Row lock: serialises concurrent borrow/return attempts on the same physical copy. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.rfidTagUid = :uid")
    Optional<Book> findByRfidTagUidForUpdate(@Param("uid") String uid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.id = :id")
    Optional<Book> findByIdForUpdate(@Param("id") Long id);

    /** keyword must be non-null; pass "" for "no filter". */
    @Query("""
            select b from Book b
            where lower(b.title) like lower(concat('%', :kw, '%'))
               or lower(b.author) like lower(concat('%', :kw, '%'))
               or lower(b.isbn) like lower(concat('%', :kw, '%'))
               or lower(coalesce(b.category, '')) like lower(concat('%', :kw, '%'))
               or lower(coalesce(b.rfidTagUid, '')) like lower(concat('%', :kw, '%'))
            """)
    Page<Book> search(@Param("kw") String keyword, Pageable pageable);
}
