package org.donriro.nastavnik.organization.educationalorganization.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.donriro.nastavnik.config.common.persistence.NamedCodedEntity;
import org.donriro.nastavnik.organization.msu.entity.Msu;

@Entity
@Table(name = "educational_organization")
@Getter
@Setter
@NoArgsConstructor
public class EducationalOrganization extends NamedCodedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "msu_id", nullable = false)
    private Msu msu;
}
