package org.donriro.nastavnik.profile.field.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class ProfileFieldRoleId implements Serializable {

    @Column(name = "profile_field_id")
    private Long profileFieldId;

    @Column(name = "role_id")
    private Short roleId;
}
