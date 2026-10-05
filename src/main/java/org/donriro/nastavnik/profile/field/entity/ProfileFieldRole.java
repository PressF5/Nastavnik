package org.donriro.nastavnik.profile.field.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.donriro.nastavnik.user.entity.Role;

@Entity
@Table(name = "profile_field_role")
@Getter
@Setter
@NoArgsConstructor
public class ProfileFieldRole {

    // указываем, что первичный ключ является составным и его структура вынесена в отдельный класс ProfileFieldRoleId который помечен @Embeddable.
    @EmbeddedId
    private ProfileFieldRoleId id;

    // Аннотация @MapsId используется для того чтобы проинициализировать id дочерней сущности значением id родительской сущности
    // @MapsId используется в отношениях @ManyToOne или @OneToOne, т.е. значения в полях profileFieldId и roleId будут такими же как в id из Role и ProfileField
    // привязываем поле id из ProfileField к полю profileFieldId из ProfileFieldRoleId поэтому указываем название поля profileFieldId из ProfileFieldRoleId в аннотации @MapsId
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("profileFieldId")
    @JoinColumn(name = "profile_field_id", nullable = false)
    private ProfileField profileField;

    // привязываем поле id из Role к полю roleId из ProfileFieldRoleId поэтому указываем название поля roleId из ProfileFieldRoleId в аннотации @MapsId
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("roleId")
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean required = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
