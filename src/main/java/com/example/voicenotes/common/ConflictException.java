package com.example.voicenotes.common;

/** Request clashes with existing data (duplicate sequence number, reused idempotency key...). Mapped to 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
