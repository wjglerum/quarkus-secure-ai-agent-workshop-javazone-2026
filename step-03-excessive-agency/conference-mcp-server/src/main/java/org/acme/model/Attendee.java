package org.acme.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "attendee")
public class Attendee extends PanacheEntityBase {

    @Id
    @Column(name = "username", nullable = false, unique = true)
    public String username;

    @Column(name = "full_name", nullable = false)
    public String fullName;

    @Column(name = "email", nullable = false)
    public String email;

    @Column(name = "ticket_tier", nullable = false)
    public String ticketTier;

    @Column(name = "dietary", nullable = false)
    public String dietary;

    public Attendee() {
    }
}
