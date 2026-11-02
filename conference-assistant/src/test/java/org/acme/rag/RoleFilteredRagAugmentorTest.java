package org.acme.rag;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class RoleFilteredRagAugmentorTest {

    private static final Query FEES_QUERY = Query.from("What are the speaker fees and reviewer scores?");

    @Inject
    RoleFilteredRagAugmentor augmentor;

    @Test
    @TestSecurity(user = "alice", roles = "attendee")
    void attendeeDoesNotRetrieveInternalFees() {
        assertFalse(retrievedText().contains("Fee:"), "attendee must not retrieve the internal fees document");
    }

    @Test
    @TestSecurity(user = "bob", roles = {"attendee", "organizer"})
    void organizerRetrievesInternalFees() {
        assertTrue(retrievedText().contains("Fee:"), "organizer must retrieve the internal fees document");
    }

    private String retrievedText() {
        List<Content> contents = augmentor.retriever().retrieve(FEES_QUERY);
        StringBuilder sb = new StringBuilder();
        for (Content content : contents) {
            sb.append(content.textSegment().text()).append("\n");
        }
        return sb.toString();
    }
}
