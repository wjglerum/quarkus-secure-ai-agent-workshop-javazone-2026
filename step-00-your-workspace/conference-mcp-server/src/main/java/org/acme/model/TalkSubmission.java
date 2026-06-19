package org.acme.model;

public record TalkSubmission(
        String id,
        String title,
        String speaker,
        String abstractText,
        boolean accepted) {
}
