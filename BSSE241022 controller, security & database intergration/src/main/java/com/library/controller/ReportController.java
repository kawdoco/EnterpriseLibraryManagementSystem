package com.library.controller;

import com.library.service.AuditService;
import com.library.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/reports")
public class ReportController {

    private final DashboardService dashboardService;
    private final AuditService auditService;

    public ReportController(DashboardService dashboardService, AuditService auditService) {
        this.dashboardService = dashboardService;
        this.auditService = auditService;
    }

    @GetMapping
    public String index(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("stats", dashboardService.summary());
        model.addAttribute("audit", auditService.recent(Flash.page(page)));
        model.addAttribute("q", "");
        return "reports/index";
    }
}
