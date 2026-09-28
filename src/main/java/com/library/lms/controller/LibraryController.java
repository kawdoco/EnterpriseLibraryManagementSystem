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

    @GetMapping("/")
    public String dashboard(Model model) {
        List<Book> allBooks = bookRepository.findAll();
        List<User> allUsers = userRepository.findAll();

        model.addAttribute("books", allBooks);
        model.addAttribute("users", allUsers);

        return "index";
    }

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