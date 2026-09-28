package com.library.lms.config;

import com.library.lms.model.Book;
import com.library.lms.model.Role;
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
            // User constructor signature: User(studentId, name, email, rfidTagId, role)
            userRepository.save(new User("ST1001", "Alice Perera", "alice@example.com", "RFID-ST-001", Role.ADMIN));
            userRepository.save(new User("ST1002", "Nimal Silva", "nimal@example.com", "RFID-ST-002", Role.STUDENT));
            userRepository.save(new User("ST1003", "Kamal Fernando", "kamal@example.com", "RFID-ST-003", Role.STUDENT));
        }

        if (bookRepository.count() == 0) {
            bookRepository.save(new Book("Clean Code", "Robert C. Martin", "9780132350884", "RFID-BK-0001", 5));
            bookRepository.save(new Book("Effective Java", "Joshua Bloch", "9780134685991", "RFID-BK-0002", 3));
            bookRepository.save(new Book("Spring in Action", "Craig Walls", "9781617294945", "RFID-BK-0003", 4));
            bookRepository.save(new Book("The Pragmatic Programmer", "Andrew Hunt", "9780201616224", "RFID-BK-0004", 2));
            bookRepository.save(new Book("Design Patterns", "Erich Gamma", "9780201633610", "RFID-BK-0005", 3));
        }
    }
}