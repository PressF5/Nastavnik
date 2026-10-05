package org.donriro.nastavnik.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.donriro.nastavnik.user.status.AuditSource;

@Entity
@Table(name = "user_audit_log")
@Getter
@Setter
@NoArgsConstructor
public class UserAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(nullable = false, length = 40)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_account_status", length = 20)
    private AccountStatus oldAccountStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_account_status", length = 20)
    private AccountStatus newAccountStatus;

    @Column(name = "old_is_blocked")
    private Boolean oldIsBlocked;

    @Column(name = "new_is_blocked")
    private Boolean newIsBlocked;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuditSource source = AuditSource.USER;
}
