package com.library.lms.model;

import jakarta.persistence.*;
import com.library.lms.model.FineStatus; 

@Entity
@Table(name = "fines")
public class Fine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    private double amount;

    @Enumerated(EnumType.STRING)
    private FineStatus status = FineStatus.UNPAID;

    public Fine() {}

    public Fine(Transaction transaction, double amount) {
        this.transaction = transaction;
        this.amount = amount;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public Transaction getTransaction() { return transaction; }
    public void setTransaction(Transaction transaction) { this.transaction = transaction; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public FineStatus getStatus() { return status; }
    public void setStatus(FineStatus status) { this.status = status; }
}