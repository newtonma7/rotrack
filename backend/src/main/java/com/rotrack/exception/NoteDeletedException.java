package com.rotrack.exception;

public class NoteDeletedException extends RuntimeException {
    public NoteDeletedException() {
        super("The Note was deleted");
    }
}
