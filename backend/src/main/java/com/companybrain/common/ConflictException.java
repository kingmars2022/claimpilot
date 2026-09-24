package com.companybrain.common;

/** The request is valid but clashes with existing data, for example a duplicate username. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
