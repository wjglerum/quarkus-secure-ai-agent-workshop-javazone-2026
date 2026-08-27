package org.acme.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "session")
public class Session extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false)
    public String id;

    @Column(name = "title", nullable = false)
    public String title;

    @Column(name = "speaker", nullable = false)
    public String speaker;

    @Column(name = "room", nullable = false)
    public String room;

    @Column(name = "accepted", nullable = false)
    public boolean accepted;

    public Session() {
    }
}
