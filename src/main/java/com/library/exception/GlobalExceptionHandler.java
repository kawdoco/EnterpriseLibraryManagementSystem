package com.library.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.mail.MailException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One place that turns exceptions into a friendly error page (browser) or JSON (/api/**).
 * Technical details go to the log only; users see safe, plain messages.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(LibraryException.class)
    public Object handleLibrary(LibraryException ex, HttpServletRequest req) {
        log.warn("Business rule rejected request [{}]: {}", ex.getCode(), ex.getMessage());
        return respond(req, statusFor(ex.getCode()), titleFor(ex.getCode()), ex.getMessage());
    }

    @ExceptionHandler(BindException.class)
    public Object handleValidation(BindException ex, HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return respond(req, HttpStatus.BAD_REQUEST, "Invalid data", message.isBlank() ? "The submitted data is invalid." : message);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public Object handleBadRequest(Exception ex, HttpServletRequest req) {
        log.debug("Bad request: {}", ex.getMessage());
        return respond(req, HttpStatus.BAD_REQUEST, "Bad request", "The request contained an invalid or missing value.");
    }

    @ExceptionHandler({CannotGetJdbcConnectionException.class, DataAccessResourceFailureException.class})
    public Object handleDatabaseDown(Exception ex, HttpServletRequest req) {
        log.error("Database unavailable", ex);
        return respond(req, HttpStatus.SERVICE_UNAVAILABLE, "Database unavailable",
                "The library database cannot be reached right now. Please try again shortly or contact the administrator.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Object handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return respond(req, HttpStatus.CONFLICT, "Conflict",
                "This change conflicts with existing data (for example a duplicate code or RFID UID, or a record that is still in use).");
    }

    @ExceptionHandler(DataAccessException.class)
    public Object handleDataAccess(DataAccessException ex, HttpServletRequest req) {
        log.error("Database error", ex);
        return respond(req, HttpStatus.INTERNAL_SERVER_ERROR, "Database error",
                "A database error occurred. Nothing was changed. Please try again.");
    }

    @ExceptionHandler(MailException.class)
    public Object handleMail(MailException ex, HttpServletRequest req) {
        log.error("Email delivery failed: {}", ex.getMessage());
        return respond(req, HttpStatus.BAD_GATEWAY, "Email not sent",
                "The email could not be delivered. Check the SMTP settings.");
    }

    /** Last resort. Spring's own HTTP errors (404, 405, ...) keep their proper status. */
    @ExceptionHandler(Exception.class)
    public Object handleAny(Exception ex, HttpServletRequest req) throws Exception {
        if (ex instanceof AccessDeniedException) {
            throw ex;                                   // let Spring Security answer with 403 / login redirect
        }
        if (ex instanceof ErrorResponse er) {
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            log.debug("HTTP {} for {}: {}", status.value(), req.getRequestURI(), ex.getMessage());
            return respond(req, status, status.getReasonPhrase(), friendly(status));
        }
        log.error("Unexpected error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return respond(req, HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong",
                "An unexpected error occurred. The problem has been logged.");
    }

    // ---------------------------------------------------------------- helpers

    private Object respond(HttpServletRequest req, HttpStatus status, String title, String message) {
        if (isApi(req)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", status.value());
            body.put("error", title);
            body.put("message", message);
            return ResponseEntity.status(status).body(body);
        }
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("title", title);
        mav.addObject("message", message);
        return mav;
    }

    private boolean isApi(HttpServletRequest req) {
        String uri = req.getRequestURI();
        String accept = req.getHeader("Accept");
        return uri.startsWith(req.getContextPath() + "/api/")
                || (accept != null && accept.contains("application/json") && !accept.contains("text/html"));
    }

    private static String friendly(HttpStatus status) {
        return switch (status.value()) {
            case 404 -> "The page or record you asked for was not found.";
            case 405 -> "That action is not allowed here.";
            case 400 -> "The request was invalid.";
            default -> "The request could not be completed.";
        };
    }

    private static HttpStatus statusFor(ErrorCode code) {
        return switch (code) {
            case NOT_FOUND, UNKNOWN_UID -> HttpStatus.NOT_FOUND;
            case DUPLICATE, BOOK_ALREADY_BORROWED -> HttpStatus.CONFLICT;
            case INVALID_UID, INVALID_PAYMENT -> HttpStatus.BAD_REQUEST;
            case HARDWARE -> HttpStatus.SERVICE_UNAVAILABLE;
            case EMAIL -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }

    private static String titleFor(ErrorCode code) {
        return switch (code) {
            case NOT_FOUND -> "Not found";
            case UNKNOWN_UID -> "Unknown RFID tag";
            case DUPLICATE -> "Already exists";
            case MEMBER_BLACKLISTED -> "Member blacklisted";
            case MEMBER_INACTIVE -> "Member inactive";
            case BOOK_ALREADY_BORROWED -> "Book already borrowed";
            case BOOK_NOT_BORROWED -> "Book not borrowed";
            case BORROW_LIMIT_REACHED -> "Borrowing limit reached";
            case HARDWARE -> "Reader unavailable";
            case EMAIL -> "Email failed";
            default -> "Request not allowed";
        };
    }
}
