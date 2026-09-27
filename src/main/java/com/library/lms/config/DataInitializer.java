package com.library.lms.config;

import com.library.lms.model.Book;
import com.library.lms.model.User;
import com.library.lms.repository.BookRepository;
import com.library.lms.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds a handful of demo users and books the first time the app runs
 * against an empty database, so the dashboard has something to show and
 * RFID tags to test scans against immediately.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    public DataInitializer(BookRepository bookRepository, UserRepository userRepository) {
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            userRepository.save(new User(null, "Alice Perera", "alice@example.com", "0771234567", "ADMIN", false));
            userRepository.save(new User(null, "Nimal Silva", "nimal@example.com", "0779876543", "MEMBER", false));
            userRepository.save(new User(null, "Kamal Fernando", "kamal@example.com", "0711122334", "MEMBER", false));
        }

        if (bookRepository.count() == 0) {
            bookRepository.save(new Book(null, "Clean Code", "Robert C. Martin", "RFID-0001", "AVAILABLE", 0));
            bookRepository.save(new Book(null, "Effective Java", "Joshua Bloch", "RFID-0002", "AVAILABLE", 0));
            bookRepository.save(new Book(null, "Spring in Action", "Craig Walls", "RFID-0003", "AVAILABLE", 0));
            bookRepository.save(new Book(null, "The Pragmatic Programmer", "Andrew Hunt", "RFID-0004", "AVAILABLE", 0));
            bookRepository.save(new Book(null, "Design Patterns", "Erich Gamma", "RFID-0005", "AVAILABLE", 0));
        }
    }
}
