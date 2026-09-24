package com.claimpilot.claim;

public enum Relationship {
    SELF("Self"),
    SPOUSE("Spouse"),
    CHILD("Child");

    private final String label;

    Relationship(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
