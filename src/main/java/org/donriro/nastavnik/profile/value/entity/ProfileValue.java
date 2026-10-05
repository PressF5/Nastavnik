package org.donriro.nastavnik.profile.value.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.donriro.nastavnik.config.common.persistence.BaseEntity;
import org.donriro.nastavnik.profile.field.entity.ProfileField;
import org.donriro.nastavnik.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "profile_value")
@Getter
@Setter
@NoArgsConstructor
public class ProfileValue extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_field_id", nullable = false)
    private ProfileField profileField;

    @Column(name = "value_text")
    private String valueText;

    @Column(name = "value_number")
    private BigDecimal valueNumber;

    @Column(name = "value_boolean")
    private Boolean valueBoolean;

    @Column(name = "value_date")
    private LocalDate valueDate;
}
