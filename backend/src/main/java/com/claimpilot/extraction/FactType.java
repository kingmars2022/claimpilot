package com.claimpilot.extraction;

/** How a fact's value is normalized and checked against its quote. */
public enum FactType {
    TEXT,
    PHONE,
    /** Stored as ISO yyyy-MM-dd. */
    DATE,
    /** Stored as a plain decimal with two places, for example 85.00. */
    AMOUNT
}
