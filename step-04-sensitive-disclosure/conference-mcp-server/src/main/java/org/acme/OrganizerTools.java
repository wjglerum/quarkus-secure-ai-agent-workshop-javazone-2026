package org.acme;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@RolesAllowed("organizer")
public class OrganizerTools {

    @Inject
    ConferenceData conferenceData;

    @Tool(name = "accept_talk", description = "Accept a talk submission by its ID. Marks the submission as accepted.")
    ToolResponse acceptTalk(String talkId) {
        boolean success = conferenceData.acceptTalk(talkId);
        if (success) {
            return ToolResponse.success(new TextContent("Talk " + talkId + " has been accepted."));
        }
        return ToolResponse.success(new TextContent("No talk found with ID: " + talkId));
    }

    @Tool(name = "issue_comp_ticket", description = "Issue a complimentary ticket to the given email address.")
    ToolResponse issueCompTicket(String email) {
        return ToolResponse.success(new TextContent("Complimentary ticket issued to: " + email));
    }

    @Tool(name = "email_all_attendees", description = "Send a broadcast message to all attendees. Returns a confirmation with the number of recipients.")
    ToolResponse emailAllAttendees(String message) {
        int count = conferenceData.allAttendees().size();
        return ToolResponse.success(new TextContent("Broadcast sent to " + count + " attendees: " + message));
    }
}
