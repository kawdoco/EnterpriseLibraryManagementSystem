package com.library.controller;

import com.library.dto.MemberForm;
import com.library.entity.LibraryMember;
import com.library.exception.LibraryException;
import com.library.service.FineService;
import com.library.service.MemberService;
import com.library.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/members")
public class MemberController {

    private final MemberService memberService;
    private final FineService fineService;
    private final TransactionService transactionService;

    public MemberController(MemberService memberService, FineService fineService,
                            TransactionService transactionService) {
        this.memberService = memberService;
        this.fineService = fineService;
        this.transactionService = transactionService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        Page<LibraryMember> members = memberService.search(q,
                Flash.page(page, Sort.by(Sort.Direction.DESC, "createdAt")));
        model.addAttribute("members", members);
        model.addAttribute("q", q);
        return "members/list";
    }

    @GetMapping("/blacklisted")
    public String blacklisted(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<LibraryMember> members = memberService.blacklisted(
                Flash.page(page, Sort.by(Sort.Direction.ASC, "fullName")));
        Map<Long, BigDecimal> outstanding = new LinkedHashMap<>();
        for (LibraryMember m : members) {
            outstanding.put(m.getId(), fineService.outstandingForMember(m.getId()));
        }
        model.addAttribute("members", members);
        model.addAttribute("outstanding", outstanding);
        model.addAttribute("q", "");
        return "members/blacklisted";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("memberForm", new MemberForm());
        model.addAttribute("editing", false);
        return "members/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("memberForm") MemberForm form, BindingResult result,
                         Model model, Principal principal, RedirectAttributes ra) {
        model.addAttribute("editing", false);
        if (result.hasErrors()) return "members/form";
        try {
            LibraryMember m = memberService.register(form, principal.getName());
            ra.addFlashAttribute("success", "Member " + m.getFullName() + " registered (" + m.getMemberCode() + ").");
            return "redirect:/members/" + m.getId();
        } catch (LibraryException e) {
            FormErrors.apply(result, e, "rfidCardUid");
            return "members/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        LibraryMember m = memberService.get(id);
        model.addAttribute("member", m);
        model.addAttribute("outstanding", fineService.outstandingForMember(id));
        model.addAttribute("loans", transactionService.activeLoans(id));
        return "members/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("memberForm", toForm(memberService.get(id)));
        model.addAttribute("editing", true);
        model.addAttribute("memberId", id);
        return "members/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("memberForm") MemberForm form,
                         BindingResult result, Model model, Principal principal, RedirectAttributes ra) {
        model.addAttribute("editing", true);
        model.addAttribute("memberId", id);
        if (result.hasErrors()) return "members/form";
        try {
            memberService.update(id, form, principal.getName());
            ra.addFlashAttribute("success", "Member updated.");
            return "redirect:/members/" + id;
        } catch (LibraryException e) {
            FormErrors.apply(result, e, "rfidCardUid");
            return "members/form";
        }
    }

    @PostMapping("/{id}/card")
    public String assignCard(@PathVariable Long id, @RequestParam String rfidCardUid,
                             Principal principal, RedirectAttributes ra) {
        return Flash.run(ra, "/members/" + id, "NFC card assigned.",
                () -> memberService.assignCard(id, rfidCardUid, principal.getName()));
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        return Flash.run(ra, "/members/" + id, "Member deactivated.",
                () -> memberService.deactivate(id, principal.getName()));
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        return Flash.run(ra, "/members/" + id, "Member reactivated.",
                () -> memberService.activate(id, principal.getName()));
    }

    @PostMapping("/{id}/blacklist")
    public String blacklist(@PathVariable Long id, @RequestParam String reason,
                            Principal principal, RedirectAttributes ra) {
        return Flash.run(ra, "/members/" + id, "Member blacklisted.",
                () -> fineService.blacklistMember(id, principal.getName(), reason));
    }

    @PostMapping("/{id}/clear-blacklist")
    public String clearBlacklist(@PathVariable Long id, @RequestParam String reason,
                                 @RequestParam(defaultValue = "false") boolean override,
                                 @RequestParam(defaultValue = "/members/blacklisted") String returnTo,
                                 Principal principal, RedirectAttributes ra) {
        // Only whitelisted internal destinations; never an arbitrary URL.
        String target = returnTo.equals("/members/" + id) ? returnTo : "/members/blacklisted";
        return Flash.run(ra, target, "Blacklist cleared and recorded in the audit log.",
                () -> fineService.clearBlacklist(id, principal.getName(), reason, override));
    }

    private static MemberForm toForm(LibraryMember m) {
        MemberForm f = new MemberForm();
        f.setId(m.getId());
        f.setMemberCode(m.getMemberCode());
        f.setFullName(m.getFullName());
        f.setEmail(m.getEmail());
        f.setPhone(m.getPhone());
        f.setAddress(m.getAddress());
        f.setRfidCardUid(m.getRfidCardUid());
        return f;
    }
}
