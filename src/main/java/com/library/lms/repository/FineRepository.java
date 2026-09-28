package com.library.lms.repository;

import com.library.lms.model.Fine;
import com.library.lms.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FineRepository extends JpaRepository<Fine, Long> {

    // Status එක String එකක් ලෙස භාවිතා වන විට
    List<Fine> findByTransactionUserAndStatus(User user, String status);

    List<Fine> findByTransactionUser(User user);
}