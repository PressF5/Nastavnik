package org.donriro.nastavnik.event.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "event_speaker")
@Getter
@Setter
@NoArgsConstructor
public class EventSpeaker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "full_name", nullable = false, length = 300)
    private String fullName;

    @Column(name = "position", length = 300)
    private String position;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}