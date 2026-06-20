package org.acme;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.acme.model.Attendee;
import org.acme.model.Booking;
import org.acme.model.Session;
import org.acme.model.TalkSubmission;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ConferenceData {

    public Optional<Attendee> attendeeByUsername(String username) {
        return Attendee.find("username", username).firstResultOptional();
    }

    public List<Attendee> allAttendees() {
        return Attendee.listAll();
    }

    /**
     * Returns accepted sessions that the given username has booked.
     * If the attendee has no bookings yet, returns all accepted sessions.
     */
    public List<Session> scheduleFor(String username) {
        List<Booking> bookings = Booking.find("username", username).list();
        if (bookings.isEmpty()) {
            return Session.find("accepted", true).list();
        }
        List<String> sessionIds = bookings.stream().map(b -> b.sessionId).toList();
        return Session.<Session>find("accepted = true and id in ?1", sessionIds).list();
    }

    /**
     * Books a session for an attendee. Returns true if the session exists and is accepted.
     */
    @Transactional
    public boolean book(String username, String sessionId) {
        Optional<Session> session = Session.<Session>find("id = ?1 and accepted = true", sessionId).firstResultOptional();
        if (session.isEmpty()) {
            return false;
        }
        Booking booking = new Booking(username, sessionId);
        booking.persist();
        return true;
    }

    public Optional<TalkSubmission> talk(String id) {
        return TalkSubmission.find("id", id).firstResultOptional();
    }

    public List<TalkSubmission> allTalks() {
        return TalkSubmission.listAll();
    }

    /**
     * Accepts a talk submission by id. Returns true if found and updated.
     */
    @Transactional
    public boolean acceptTalk(String id) {
        Optional<TalkSubmission> found = TalkSubmission.<TalkSubmission>find("id", id).firstResultOptional();
        if (found.isEmpty()) {
            return false;
        }
        TalkSubmission t = found.get();
        t.accepted = true;
        return true;
    }
}
