package com.library.lms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a library user/member. Role distinguishes staff (ADMIN) from
 * regular borrowers (MEMBER). isBlacklisted is automatically toggled by
 * LibraryService when accrued fines exceed the configured threshold.
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    /**
     * Expected values: "ADMIN", "MEMBER".
     */
    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private boolean isBlacklisted = false;
}
