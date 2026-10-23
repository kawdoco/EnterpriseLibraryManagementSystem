package com.library.controller;

import com.library.exception.LibraryException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Small shared helpers for controllers: flash messages and paging. */
final class Flash {

    static final int PAGE_SIZE = 10;

    private Flash() {}

    /** Runs the action; shows its success message, or the business-rule message on failure. */
    static String run(RedirectAttributes ra, String redirect, String success, Runnable action) {
        try {
            action.run();
            ra.addFlashAttribute("success", success);
        } catch (LibraryException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + redirect;
    }

    static Pageable page(int page, Sort sort) {
        return PageRequest.of(Math.max(page, 0), PAGE_SIZE, sort);
    }

    static Pageable page(int page) {
        return PageRequest.of(Math.max(page, 0), PAGE_SIZE);
    }
}
