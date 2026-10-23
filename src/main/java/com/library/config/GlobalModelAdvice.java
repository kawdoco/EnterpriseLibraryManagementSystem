package com.library.config;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Values every Thymeleaf page may need. Exposes only safe business settings, never secrets. */
@ControllerAdvice
public class GlobalModelAdvice {

    private final LibraryProperties props;

    public GlobalModelAdvice(LibraryProperties props) {
        this.props = props;
    }

    @ModelAttribute("libraryName")
    public String libraryName() { return props.getContact().getName(); }

    @ModelAttribute("currency")
    public String currency() { return props.getCurrency(); }

    @ModelAttribute("today")
    public LocalDate today() { return LocalDate.now(); }

    @ModelAttribute("blacklistThreshold")
    public BigDecimal blacklistThreshold() { return props.getBlacklistThreshold(); }

    @ModelAttribute("finePerDay")
    public BigDecimal finePerDay() { return props.getFineRatePerDay(); }

    @ModelAttribute("borrowDays")
    public int borrowDays() { return props.getBorrowDays(); }

    @ModelAttribute("maxLoans")
    public int maxLoans() { return props.getMaxActiveLoans(); }
}
