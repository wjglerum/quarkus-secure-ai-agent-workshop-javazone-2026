package org.acme.guardrails;

import dev.langchain4j.guardrail.GuardrailResult;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.data.message.AiMessage.from;
import static dev.langchain4j.test.guardrail.GuardrailAssertions.assertThat;

class SensitiveDisclosureGuardTest {

    private SensitiveDisclosureGuard newGuard() {
        SensitiveDisclosureGuard guard = new SensitiveDisclosureGuard();
        guard.markersCsv = "INTERNAL - ORGANIZERS ONLY,Fee:,Reviewer score average:";
        return guard;
    }

    @Test
    void normalAnswer_isSuccessful() {
        assertThat(newGuard().validate(from("The next talk starts at 10:00 in Hall A.")))
                .isSuccessful();
    }

    @Test
    void internalMarker_isBlocked() {
        var res = newGuard().validate(from("INTERNAL - ORGANIZERS ONLY\n\nSpeaker Fees..."));
        org.assertj.core.api.Assertions.assertThat(res.isSuccess()).isFalse();
    }

    @Test
    void feeMarker_isBlocked() {
        var res = newGuard().validate(from("Fee: 2500 EUR for alice"));
        org.assertj.core.api.Assertions.assertThat(res.isSuccess()).isFalse();
    }

    @Test
    void reviewerScoreMarker_isBlocked() {
        var res = newGuard().validate(from("Reviewer score average: 4.7 / 5.0"));
        org.assertj.core.api.Assertions.assertThat(res.isSuccess()).isFalse();
    }

    @Test
    void detectionIsCaseInsensitive() {
        var res = newGuard().validate(from("internal - organizers only header present"));
        org.assertj.core.api.Assertions.assertThat(res.isSuccess()).isFalse();
    }
}
