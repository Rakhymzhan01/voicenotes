package com.example.voicenotes.common;

/** Operation not allowed in the entity's current status. Mapped to 409. */
public class InvalidStateException extends RuntimeException {
    public InvalidStateException(String message) {
        super(message);
    }
}
