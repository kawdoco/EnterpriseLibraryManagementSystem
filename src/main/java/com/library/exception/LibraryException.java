package com.library.exception;

/** Business-rule failure with a machine-readable code and a user-safe message. */
public class LibraryException extends RuntimeException {

    private final ErrorCode code;

    public LibraryException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public LibraryException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode getCode() { return code; }

    public static LibraryException notFound(String what) {
        return new LibraryException(ErrorCode.NOT_FOUND, what + " not found.");
    }

    public static LibraryException duplicate(String message) {
        return new LibraryException(ErrorCode.DUPLICATE, message);
    }
}
