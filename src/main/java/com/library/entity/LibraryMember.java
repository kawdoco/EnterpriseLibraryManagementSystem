package com.library.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "library_members",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_member_code", columnNames = "member_code"),
                @UniqueConstraint(name = "uk_member_card_uid", columnNames = "rfid_card_uid")
        },
        indexes = {
                @Index(name = "idx_member_name", columnList = "full_name"),
                @Index(name = "idx_member_email", columnList = "email")
        })
public class LibraryMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 30)
    @Column(name = "member_code", nullable = false, length = 30)
    private String memberCode;

    @NotBlank
    @Size(max = 150)
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Email
    @Size(max = 150)
    @Column(length = 150)
    private String email;

    @Size(max = 30)
    @Column(length = 30)
    private String phone;

    @Size(max = 300)
    @Column(length = 300)
    private String address;

    /** Normalized (uppercase hex, no separators). Null until a card is assigned. */
    @Column(name = "rfid_card_uid", length = 32)
    private String rfidCardUid;

    @Column(name = "is_blacklisted", nullable = false)
    private boolean blacklisted = false;

    /** Soft-delete flag: members with history are deactivated, never removed. */
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
