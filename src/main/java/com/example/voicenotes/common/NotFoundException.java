package com.example.voicenotes.common;

/** Mapped to 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
