package com.rotrack.exception;

public class InvalidCursorException extends RuntimeException {

    public InvalidCursorException() {
        super("The history cursor is invalid");
    }
}
