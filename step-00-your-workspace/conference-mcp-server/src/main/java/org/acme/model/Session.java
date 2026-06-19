package org.acme.model;

public record Session(
        String id,
        String title,
        String speaker,
        String room,
        boolean accepted) {
}
