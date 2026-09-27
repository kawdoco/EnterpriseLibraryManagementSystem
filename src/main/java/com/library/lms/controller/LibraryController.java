package com.library.lms.controller;

import com.library.lms.dto.ScanResult;
import com.library.lms.model.Book;
import com.library.lms.model.User;
import com.library.lms.repository.BookRepository;
import com.library.lms.repository.UserRepository;
import com.library.lms.service.LibraryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class LibraryController {

    private final LibraryService libraryService;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    public LibraryController(LibraryService libraryService,
                              BookRepository bookRepository,
                              UserRepository userRepository) {
        this.libraryService = libraryService;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
    }

    /**
     * Main dashboard: full catalog, top-5 popular books, and the member list
     * used to populate the scan form's dropdown.
     */
    @GetMapping("/")
    public String dashboard(Model model) {
        List<Book> allBooks = bookRepository.findAll();
        List<Book> popularBooks = bookRepository.findTop5ByOrderByBorrowCountDesc();
        List<User> allUsers = userRepository.findAll();

        model.addAttribute("books", allBooks);
        model.addAttribute("popularBooks", popularBooks);
        model.addAttribute("users", allUsers);

        return "index";
    }

    /**
     * Handles an incoming RFID scan. Works equally well with a REST-style
     * POST or a USB HID keyboard-emulation scanner that "types" the tag
     * into the form field and submits it (e.g. via an Enter keystroke).
     */
    @PostMapping("/scan")
    public String scan(@RequestParam("rfidTag") String rfidTag,
                        @RequestParam(value = "userId", required = false) Long userId,
                        RedirectAttributes redirectAttributes) {

        ScanResult result = libraryService.handleRfidScan(rfidTag, userId);

        redirectAttributes.addFlashAttribute("scanSuccess", result.isSuccess());
        redirectAttributes.addFlashAttribute("scanMessage", result.getMessage());
        redirectAttributes.addFlashAttribute("scanAction", result.getAction());

        return "redirect:/";
    }
}
