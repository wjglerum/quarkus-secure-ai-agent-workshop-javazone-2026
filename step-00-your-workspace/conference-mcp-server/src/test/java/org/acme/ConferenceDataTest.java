package org.acme;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ConferenceDataTest {

    @Inject ConferenceData data;

    @Test
    void seedsAtLeastFourAttendeesIncludingAliceAndCarol() {
        assertTrue(data.allAttendees().size() >= 4);
        assertTrue(data.attendeeByUsername("alice").isPresent());
        assertTrue(data.attendeeByUsername("carol").isPresent());
    }

    @Test
    void seedsATalkSubmissionContainingAnInjectionPayload() {
        boolean hasInjection = data.allTalks().stream()
                .anyMatch(t -> t.abstractText().toLowerCase().contains("ignore"));
        assertTrue(hasInjection, "one seeded abstract must carry an injection payload");
    }
}
