package com.library.controller;

import com.library.exception.ErrorCode;
import com.library.exception.LibraryException;
import org.springframework.validation.BindingResult;

/** Maps a service-layer rejection onto the form so the user sees it next to the input. */
final class FormErrors {

    private FormErrors() {}

    static void apply(BindingResult result, LibraryException e, String uidField) {
        if (e.getCode() == ErrorCode.INVALID_UID && uidField != null) {
            result.rejectValue(uidField, "invalid.uid", e.getMessage());
        } else {
            result.reject("business.rule", e.getMessage());
        }
    }
}
