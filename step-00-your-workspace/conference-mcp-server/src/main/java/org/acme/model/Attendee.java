package org.acme.model;

public record Attendee(
        String username,
        String fullName,
        String email,
        String ticketTier,
        String dietary) {
}
