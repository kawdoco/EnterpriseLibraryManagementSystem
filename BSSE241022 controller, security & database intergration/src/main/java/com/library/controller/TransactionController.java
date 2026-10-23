package com.library.controller;

import com.library.model.TransactionStatus;
import com.library.service.TransactionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String q,
                       @RequestParam(required = false) TransactionStatus status,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("transactions", transactionService.list(q, status, Flash.page(page)));
        model.addAttribute("q", q);
        model.addAttribute("status", status == null ? "" : status.name());
        return "transactions/list";
    }

    @GetMapping("/overdue")
    public String overdue(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("rows", transactionService.overdue(Flash.page(page)));
        model.addAttribute("q", "");
        return "transactions/overdue";
    }
}
