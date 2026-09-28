package com.library.lms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String studentId; // e.g. ST10293

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    private String rfidTagId; // RFID Tag tied to Student ID (Optional)

    @Enumerated(EnumType.STRING)
    private Role role = Role.STUDENT;

    private boolean blacklisted = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    public User() {}

    public User(String studentId, String name, String email, String rfidTagId, Role role) {
        this.studentId = studentId;
        this.name = name;
        this.email = email;
        this.rfidTagId = rfidTagId;
        this.role = role;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRfidTagId() { return rfidTagId; }
    public void setRfidTagId(String rfidTagId) { this.rfidTagId = rfidTagId; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public boolean isBlacklisted() { return blacklisted; }
    public void setBlacklisted(boolean blacklisted) { this.blacklisted = blacklisted; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}