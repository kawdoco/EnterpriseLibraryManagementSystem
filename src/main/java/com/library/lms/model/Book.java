package com.library.lms.model;

import jakarta.persistence.*;

@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    private String isbn;

    @Column(unique = true, nullable = false)
    private String rfidTagId; // RFID Tag attached to physical book

    private int totalCopies;
    private int availableCopies;

    private boolean newArrival = true;

    public Book() {}

    public Book(String title, String author, String isbn, String rfidTagId, int totalCopies) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.rfidTagId = rfidTagId;
        this.totalCopies = totalCopies;
        this.availableCopies = totalCopies;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }

    public String getRfidTagId() { return rfidTagId; }
    public void setRfidTagId(String rfidTagId) { this.rfidTagId = rfidTagId; }

    public int getTotalCopies() { return totalCopies; }
    public void setTotalCopies(int totalCopies) { this.totalCopies = totalCopies; }

    public int getAvailableCopies() { return availableCopies; }
    public void setAvailableCopies(int availableCopies) { this.availableCopies = availableCopies; }

    public boolean isNewArrival() { return newArrival; }
    public void setNewArrival(boolean newArrival) { this.newArrival = newArrival; }
}