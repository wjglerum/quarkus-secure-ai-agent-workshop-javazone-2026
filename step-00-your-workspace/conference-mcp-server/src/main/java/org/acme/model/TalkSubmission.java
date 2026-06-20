package org.acme.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "talk_submission")
public class TalkSubmission extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false)
    public String id;

    @Column(name = "title", nullable = false)
    public String title;

    @Column(name = "speaker", nullable = false)
    public String speaker;

    @Column(name = "abstract_text", nullable = false, length = 2000)
    public String abstractText;

    @Column(name = "accepted", nullable = false)
    public boolean accepted;

    public TalkSubmission() {
    }
}
