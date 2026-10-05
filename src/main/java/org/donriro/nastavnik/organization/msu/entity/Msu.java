package org.donriro.nastavnik.organization.msu.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.donriro.nastavnik.config.common.persistence.NamedCodedEntity;
import org.donriro.nastavnik.organization.educationalorganization.entity.EducationalOrganization;
import org.donriro.nastavnik.organization.region.entity.Region;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "msu")
@Getter
@Setter
@NoArgsConstructor
public class Msu extends NamedCodedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @OneToMany(mappedBy = "msu", fetch = FetchType.LAZY)
    private List<EducationalOrganization> educationalOrganizations = new ArrayList<>();
}
