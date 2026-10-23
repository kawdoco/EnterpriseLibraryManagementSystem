package com.library.controller;

import com.library.dto.BookForm;
import com.library.entity.Book;
import com.library.exception.LibraryException;
import com.library.service.BookService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        Page<Book> books = bookService.search(q, Flash.page(page, Sort.by(Sort.Direction.DESC, "createdAt")));
        model.addAttribute("books", books);
        model.addAttribute("q", q);
        return "books/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("bookForm", new BookForm());
        model.addAttribute("editing", false);
        return "books/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("bookForm") BookForm form, BindingResult result,
                         Model model, Principal principal, RedirectAttributes ra) {
        model.addAttribute("editing", false);
        if (result.hasErrors()) return "books/form";
        try {
            Book b = bookService.register(form, principal.getName());
            ra.addFlashAttribute("success", "Book \"" + b.getTitle() + "\" registered.");
            return "redirect:/books";
        } catch (LibraryException e) {
            FormErrors.apply(result, e, "rfidTagUid");
            return "books/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Book b = bookService.get(id);
        model.addAttribute("bookForm", toForm(b));
        model.addAttribute("book", b);
        model.addAttribute("editing", true);
        return "books/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("bookForm") BookForm form,
                         BindingResult result, Model model, Principal principal, RedirectAttributes ra) {
        model.addAttribute("editing", true);
        model.addAttribute("book", bookService.get(id));
        if (result.hasErrors()) return "books/form";
        try {
            bookService.update(id, form, principal.getName());
            ra.addFlashAttribute("success", "Book updated.");
            return "redirect:/books";
        } catch (LibraryException e) {
            FormErrors.apply(result, e, "rfidTagUid");
            return "books/form";
        }
    }

    @PostMapping("/{id}/tag")
    public String assignTag(@PathVariable Long id, @RequestParam String rfidTagUid,
                            Principal principal, RedirectAttributes ra) {
        return Flash.run(ra, "/books/" + id + "/edit", "RFID tag assigned.",
                () -> bookService.assignTag(id, rfidTagUid, principal.getName()));
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        return Flash.run(ra, "/books", "Book deactivated.", () -> bookService.deactivate(id, principal.getName()));
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        return Flash.run(ra, "/books", "Book reactivated.", () -> bookService.activate(id, principal.getName()));
    }

    private static BookForm toForm(Book b) {
        BookForm f = new BookForm();
        f.setId(b.getId());
        f.setIsbn(b.getIsbn());
        f.setTitle(b.getTitle());
        f.setAuthor(b.getAuthor());
        f.setPublisher(b.getPublisher());
        f.setCategory(b.getCategory());
        f.setPublicationYear(b.getPublicationYear());
        f.setRfidTagUid(b.getRfidTagUid());
        return f;
    }
}
