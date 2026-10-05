package org.donriro.nastavnik.organization.region.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.donriro.nastavnik.config.common.persistence.NamedCodedEntity;
import org.donriro.nastavnik.organization.msu.entity.Msu;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "region")
@Getter
@Setter
@NoArgsConstructor
public class Region extends NamedCodedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "region", fetch = FetchType.LAZY)
    private List<Msu> msus = new ArrayList<>();
}
