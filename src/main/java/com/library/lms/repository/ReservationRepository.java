package com.library.lms.repository;

import com.library.lms.model.Book;
import com.library.lms.model.Reservation;
import com.library.lms.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByBookAndStatusOrderByQueuePositionAsc(Book book, ReservationStatus status);
    Optional<Reservation> findFirstByBookAndStatusOrderByQueuePositionAsc(Book book, ReservationStatus status);
    int countByBookAndStatus(Book book, ReservationStatus status);
}