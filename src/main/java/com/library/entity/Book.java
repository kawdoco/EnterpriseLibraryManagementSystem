package com.library.entity;

import com.library.model.BookStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One row = one PHYSICAL copy. Several rows may share an ISBN, but each has its own RFID tag.
 */
@Getter
@Setter
@Entity
@Table(name = "books",
        uniqueConstraints = @UniqueConstraint(name = "uk_book_tag_uid", columnNames = "rfid_tag_uid"),
        indexes = {
                @Index(name = "idx_book_isbn", columnList = "isbn"),
                @Index(name = "idx_book_title", columnList = "title"),
                @Index(name = "idx_book_borrow_count", columnList = "borrow_count")
        })
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ISBN-10 or ISBN-13, stored without hyphens/spaces. Not unique: multiple copies allowed. */
    @NotBlank
    @Size(max = 20)
    @Column(nullable = false, length = 20)
    private String isbn;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String title;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String author;

    @Size(max = 255)
    private String publisher;

    @Size(max = 100)
    @Column(length = 100)
    private String category;

    @Column(name = "publication_year")
    private Integer publicationYear;

    /** Normalized (uppercase hex, no separators). Null until a tag is assigned. */
    @Column(name = "rfid_tag_uid", length = 32)
    private String rfidTagUid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookStatus status = BookStatus.AVAILABLE;

    @Column(name = "borrow_count", nullable = false)
    private int borrowCount = 0;

    /** Soft-delete flag. */
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
        if (status == null) status = BookStatus.AVAILABLE;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
