package org.donriro.nastavnik.profile.value.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.donriro.nastavnik.profile.field.entity.ProfileField;
import org.donriro.nastavnik.profile.field.entity.ProfileFieldOption;

@Entity
@Table(name = "profile_value_option")
@Getter
@Setter
@NoArgsConstructor
public class ProfileValueOption {

    @EmbeddedId
    private ProfileValueOptionId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("profileValueId")
    @JoinColumn(name = "profile_value_id", nullable = false)
    private ProfileValue profileValue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("profileOptionId")
    @JoinColumn(name = "profile_option_id", nullable = false)
    private ProfileFieldOption profileOption;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_field_id", nullable = false)
    private ProfileField profileField;
}
