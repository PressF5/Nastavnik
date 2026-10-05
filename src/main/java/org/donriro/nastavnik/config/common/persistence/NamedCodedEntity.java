package org.donriro.nastavnik.config.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@MappedSuperclass
@Getter
@Setter
public abstract class NamedCodedEntity extends BaseEntity {

    @Column(name = "code", nullable = false)
    private Integer code;

    @Column(name = "name", nullable = false, length = 500)
    private String name;
}
