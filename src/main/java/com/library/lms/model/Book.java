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
 * A physical library book, identified for scanning purposes by the unique
 * RFID tag attached to it. borrowCount is incremented every time the book
 * is checked out and powers the "popular books" analytics query.
 */
@Entity
@Table(name = "books")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false, unique = true)
    private String rfidTag;

    /**
     * Expected values: "AVAILABLE", "BORROWED".
     */
    @Column(nullable = false)
    private String status = "AVAILABLE";

    @Column(nullable = false)
    private int borrowCount = 0;
}
