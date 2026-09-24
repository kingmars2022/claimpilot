package com.claimpilot.document;

import java.util.Set;

public enum DocumentKind {

    /** An insurance policy or benefits booklet: indexed for questions and read for key facts. */
    POLICY(Set.of("pdf", "docx", "txt", "md", "png", "jpg", "jpeg")),

    /** A receipt or invoice: read for the amounts and dates that go on a claim form. */
    RECEIPT(Set.of("pdf", "png", "jpg", "jpeg", "txt"));

    private final Set<String> extensions;

    DocumentKind(Set<String> extensions) {
        this.extensions = extensions;
    }

    public Set<String> extensions() {
        return extensions;
    }
}
