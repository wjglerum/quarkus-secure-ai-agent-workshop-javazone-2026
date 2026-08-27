package org.acme;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.model.Attendee;
import org.acme.model.Session;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class AttendeeTools {

    @Inject
    ConferenceData conferenceData;

    @Tool(name = "my_profile", description = "Return the profile of the attendee with the given username: full name, email, ticket tier, and dietary requirements.")
    ToolResponse myProfile(String username) {
        Optional<Attendee> found = conferenceData.attendeeByUsername(username);
        if (found.isEmpty()) {
            return ToolResponse.success(new TextContent("No attendee found with username: " + username));
        }
        Attendee a = found.get();
        String result = "Profile for " + a.username + ":\n"
                + "Full name: " + a.fullName + "\n"
                + "Email: " + a.email + "\n"
                + "Ticket tier: " + a.ticketTier + "\n"
                + "Dietary: " + a.dietary;
        return ToolResponse.success(new TextContent(result));
    }

    @Tool(name = "my_schedule", description = "Return the list of accepted sessions booked by the attendee with the given username.")
    ToolResponse mySchedule(String username) {
        List<Session> sessions = conferenceData.scheduleFor(username);
        if (sessions.isEmpty()) {
            return ToolResponse.success(new TextContent("No sessions found for username: " + username));
        }
        StringBuilder sb = new StringBuilder("Schedule for " + username + ":\n");
        for (Session s : sessions) {
            sb.append("- [").append(s.id).append("] ").append(s.title)
                    .append(" by ").append(s.speaker)
                    .append(" in ").append(s.room).append("\n");
        }
        return ToolResponse.success(new TextContent(sb.toString()));
    }

    @Tool(name = "lookup_attendee", description = "Find an attendee by name or username and return their full profile including email and dietary requirements.")
    ToolResponse lookupAttendee(String name) {
        Optional<Attendee> byUsername = conferenceData.attendeeByUsername(name);
        if (byUsername.isPresent()) {
            Attendee a = byUsername.get();
            String result = "Attendee found:\n"
                    + "Username: " + a.username + "\n"
                    + "Full name: " + a.fullName + "\n"
                    + "Email: " + a.email + "\n"
                    + "Ticket tier: " + a.ticketTier + "\n"
                    + "Dietary: " + a.dietary;
            return ToolResponse.success(new TextContent(result));
        }
        Optional<Attendee> byName = conferenceData.allAttendees().stream()
                .filter(a -> a.fullName.equalsIgnoreCase(name))
                .findFirst();
        if (byName.isPresent()) {
            Attendee a = byName.get();
            String result = "Attendee found:\n"
                    + "Username: " + a.username + "\n"
                    + "Full name: " + a.fullName + "\n"
                    + "Email: " + a.email + "\n"
                    + "Ticket tier: " + a.ticketTier + "\n"
                    + "Dietary: " + a.dietary;
            return ToolResponse.success(new TextContent(result));
        }
        return ToolResponse.success(new TextContent("No attendee found with name or username: " + name));
    }

    @Tool(name = "book_session", description = "Book an accepted session for the attendee with the given username. Provide the session ID.")
    ToolResponse bookSession(String username, String sessionId) {
        boolean success = conferenceData.book(username, sessionId);
        if (success) {
            return ToolResponse.success(new TextContent("Session " + sessionId + " booked for " + username + "."));
        }
        return ToolResponse.success(new TextContent("Failed to book session " + sessionId + " for " + username + ". The session may not exist or may not be accepted."));
    }
}
