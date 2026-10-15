package org.donriro.nastavnik.matching.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.donriro.nastavnik.matching.type.ComparisonType;
import org.donriro.nastavnik.profile.field.entity.ProfileField;

import java.math.BigDecimal;

@Entity
@Table(name = "profile_matching_rule")
@Getter
@Setter
@NoArgsConstructor
public class ProfileMatchingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_field_id", nullable = false, unique = true)
    private ProfileField profileField;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparison_type", nullable = false, length = 40)
    private ComparisonType comparisonType;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal weight = BigDecimal.ONE;

    @Column(nullable = false)
    private boolean active = true;
}
