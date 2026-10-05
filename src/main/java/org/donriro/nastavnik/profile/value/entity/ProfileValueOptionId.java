package org.donriro.nastavnik.profile.value.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

import java.io.Serializable;

@Data
@Embeddable
public class ProfileValueOptionId implements Serializable {

    @Column(name = "profile_value_id")
    private Long profileValueId;

    @Column(name = "profile_option_id")
    private Long profileOptionId;
}

