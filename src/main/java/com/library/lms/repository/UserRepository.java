package com.library.lms.repository;

import com.library.lms.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByStudentId(String studentId);
    Optional<User> findByRfidTagId(String rfidTagId);
}