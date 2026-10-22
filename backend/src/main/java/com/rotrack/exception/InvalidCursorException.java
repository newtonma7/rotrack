package com.rotrack.exception;

public class InvalidCursorException extends RuntimeException {

    public InvalidCursorException() {
        this("The history cursor is invalid");
    }

    public InvalidCursorException(String message) {
        super(message);
    }
}
