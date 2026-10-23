package com.library.controller;

import com.library.dto.PaymentForm;
import com.library.dto.PaymentResult;
import com.library.exception.LibraryException;
import com.library.model.PaymentMethod;
import com.library.service.FineService;
import com.library.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;

@Controller
@RequestMapping("/fines")
public class FineController {

    private final FineService fineService;
    private final TransactionService transactionService;

    public FineController(FineService fineService, TransactionService transactionService) {
        this.fineService = fineService;
        this.transactionService = transactionService;
    }

    @GetMapping
    public String outstanding(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("rows", transactionService.outstandingFines(Flash.page(page)));
        model.addAttribute("totalOutstanding", fineService.totalOutstanding());
        model.addAttribute("q", "");
        return "fines/list";
    }

    @GetMapping("/payments")
    public String payments(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("payments", transactionService.payments(Flash.page(page)));
        model.addAttribute("totalCollected", fineService.totalCollected());
        model.addAttribute("q", "");
        return "fines/payments";
    }

    @GetMapping("/pay/{transactionId}")
    public String payForm(@PathVariable Long transactionId, Model model) {
        BigDecimal outstanding = fineService.outstandingForTransaction(transactionId);
        PaymentForm form = new PaymentForm();
        form.setTransactionId(transactionId);
        form.setAmount(outstanding);
        model.addAttribute("paymentForm", form);
        populate(model, transactionId);
        return "fines/pay";
    }

    @PostMapping("/pay")
    public String pay(@Valid @ModelAttribute("paymentForm") PaymentForm form, BindingResult result,
                      Model model, Principal principal, RedirectAttributes ra) {
        if (result.hasErrors()) {
            populate(model, form.getTransactionId());
            return "fines/pay";
        }
        try {
            PaymentResult r = fineService.recordPayment(form, principal.getName());
            String msg = "Payment of " + r.amountPaid() + " recorded for " + r.memberName()
                    + ". Member outstanding: " + r.memberOutstanding() + ".";
            if (r.eligibleForBlacklistClearance()) {
                msg += " This member is now below the blacklist threshold and can be cleared from the Blacklisted page.";
            }
            ra.addFlashAttribute("success", msg);
            return "redirect:/fines";
        } catch (LibraryException e) {
            FormErrors.apply(result, e, null);
            populate(model, form.getTransactionId());
            return "fines/pay";
        }
    }

    private void populate(Model model, Long transactionId) {
        model.addAttribute("tx", transactionService.getWithDetails(transactionId));
        model.addAttribute("outstanding", fineService.outstandingForTransaction(transactionId));
        model.addAttribute("paymentMethods", PaymentMethod.values());
    }
}
