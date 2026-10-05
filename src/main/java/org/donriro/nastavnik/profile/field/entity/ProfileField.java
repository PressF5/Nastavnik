package org.donriro.nastavnik.profile.field.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.donriro.nastavnik.config.common.persistence.BaseEntity;
import org.donriro.nastavnik.profile.field.type.ProfileFieldType;

@Entity
@Table(name = "profile_field")
@Getter
@Setter
@NoArgsConstructor
public class ProfileField extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 30)
    private ProfileFieldType fieldType;

    @Column(nullable = false)
    private boolean searchable;

    @Column(nullable = false)
    private boolean active = true;
}
