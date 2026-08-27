package org.acme.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "booking")
public class Booking extends PanacheEntity {

    @Column(name = "username", nullable = false)
    public String username;

    @Column(name = "session_id", nullable = false)
    public String sessionId;

    public Booking() {
    }

    public Booking(String username, String sessionId) {
        this.username = username;
        this.sessionId = sessionId;
    }
}
