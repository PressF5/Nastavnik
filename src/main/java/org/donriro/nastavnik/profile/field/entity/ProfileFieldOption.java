package org.donriro.nastavnik.profile.field.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.donriro.nastavnik.config.common.persistence.BaseEntity;

@Entity
@Table(name = "profile_field_option")
@Getter
@Setter
@NoArgsConstructor
public class ProfileFieldOption extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_field_id", nullable = false)
    private ProfileField profileField;

    @Column(nullable = false)
    private String value;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private boolean active = true;
}
