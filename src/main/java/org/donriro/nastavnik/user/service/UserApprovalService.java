package org.donriro.nastavnik.user.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.entity.UserAuditLog;
import org.donriro.nastavnik.user.repository.UserAuditLogRepository;
import org.donriro.nastavnik.user.repository.UserRepository;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.donriro.nastavnik.user.status.AuditSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserApprovalService {

    private final UserRepository userRepository;
    private final UserAuditLogRepository userAuditLogRepository;

    public List<User> getPendingUsers(Long actorId) {
        User actor = getUser(actorId);
        return userRepository.findAllByAccountStatus(AccountStatus.PENDING).stream()
                .filter(target -> canManage(actor, target))
                .toList();
    }

    @Transactional
    public User approve(Long actorId, Long targetId) {
        return changeStatus(actorId, targetId, AccountStatus.ACCEPTED);
    }

    @Transactional
    public User reject(Long actorId, Long targetId) {
        return changeStatus(actorId, targetId, AccountStatus.REJECTED);
    }

    @Transactional
    public User restoreApproval(Long actorId, Long targetId) {
        User actor = getUser(actorId);
        User target = getUser(targetId);

        if (actor.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нельзя восстановить собственную заявку.");
        }

        if (actor.getAccountStatus() != AccountStatus.ACCEPTED
                || actor.isBlocked()
                || target.getAccountStatus() != AccountStatus.REJECTED
                || !hasManagementScope(actor, target)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "У вас нет прав на восстановление этой заявки.");
        }

        AccountStatus oldStatus = target.getAccountStatus();
        target.setAccountStatus(AccountStatus.ACCEPTED);

        User savedUser = userRepository.save(target);

        UserAuditLog auditLog = new UserAuditLog();
        auditLog.setUser(target);
        auditLog.setActor(actor);
        auditLog.setAction("APPROVAL_RESTORED");
        auditLog.setOldAccountStatus(oldStatus);
        auditLog.setNewAccountStatus(AccountStatus.ACCEPTED);
        auditLog.setOldIsBlocked(target.isBlocked());
        auditLog.setNewIsBlocked(target.isBlocked());
        auditLog.setSource(AuditSource.USER);

        userAuditLogRepository.save(auditLog);

        return savedUser;
    }

    @Transactional
    public User blockUser(Long actorId, Long targetId) {
        return changeBlockedStatus(actorId, targetId, true);
    }

    @Transactional
    public User unblockUser(Long actorId, Long targetId) {
        return changeBlockedStatus(actorId, targetId, false);
    }

    public Page<User> getUsers(Long actorId, RoleCode role, AccountStatus status, Boolean blocked, Pageable pageable) {
        User actor = getUser(actorId);

        Specification<User> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Фильтр по роли.
            if (role != null) {
                predicates.add(cb.equal(root.get("role").get("code"), role));
            }

            // Фильтр по статусу аккаунта.
            if (status != null) {
                predicates.add(cb.equal(root.get("accountStatus"), status));
            }

            // Фильтр по блокировке.
            if (blocked != null) {
                predicates.add(cb.equal(root.get("blocked"), blocked));
            }

            // Ограничиваем выборку административной областью.
            switch (actor.getRole().getCode()) {
                case REGION_ADMIN -> predicates.add(cb.equal(root.get("region").get("id"), actor.getRegion().getId()));
                case MSU_ADMIN -> predicates.add(cb.equal(root.get("educationalOrganization").get("msu").get("id"), actor.getMsu().getId()));
                case OO_ADMIN -> predicates.add(cb.equal(root.get("educationalOrganization").get("id"), actor.getEducationalOrganization().getId()));
                default -> throw new AccessDeniedException("Недостаточно прав для просмотра списка пользователей.");
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return userRepository.findAll(specification, pageable);
    }

    private User changeBlockedStatus(Long actorId, Long targetId, boolean newBlockedStatus) {
        User actor = getUser(actorId);
        User target = getUser(targetId);

        // Нельзя блокировать или разблокировать собственный аккаунт.
        if (actor.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нельзя изменять статус блокировки собственного аккаунта.");
        }

        // Управлять аккаунтами может только действующий незаблокированный администратор.
        if (actor.getAccountStatus() != AccountStatus.ACCEPTED || actor.isBlocked()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "У вас нет прав на выполнение этой операции.");
        }

        // Блокировка и разблокировка применяются только к подтверждённым аккаунтам.
        if (target.getAccountStatus() != AccountStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Блокировать или разблокировать можно только подтверждённый аккаунт.");
        }

        // Проверяем роль администратора и организационную область его полномочий.
        if (!hasManagementScope(actor, target)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "У вас нет прав на управление этим пользователем.");
        }

        boolean oldBlockedStatus = target.isBlocked();

        // Не считаем повторное действие успешным изменением.
        if (oldBlockedStatus == newBlockedStatus) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, newBlockedStatus ? "Аккаунт уже заблокирован." : "Аккаунт уже разблокирован.");
        }

        target.setBlocked(newBlockedStatus);
        User savedUser = userRepository.save(target);

        // Сохраняем историю изменения блокировки.
        UserAuditLog auditLog = new UserAuditLog();
        auditLog.setUser(savedUser);
        auditLog.setActor(actor);
        auditLog.setAction(newBlockedStatus ? "ACCOUNT_BLOCKED" : "ACCOUNT_UNBLOCKED");
        auditLog.setOldAccountStatus(target.getAccountStatus());
        auditLog.setNewAccountStatus(target.getAccountStatus());
        auditLog.setOldIsBlocked(oldBlockedStatus);
        auditLog.setNewIsBlocked(newBlockedStatus);
        auditLog.setSource(AuditSource.USER);

        userAuditLogRepository.save(auditLog);

        return savedUser;
    }

    private User changeStatus(Long actorId, Long targetId, AccountStatus newStatus) {
        User actor = getUser(actorId);
        User target = getUser(targetId);
        if (actor.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нельзя согласовать собственную заявку.");
        }
        if (!canManage(actor, target)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "У вас нет прав на обработку этой заявки.");
        }
        if (target.getAccountStatus() != AccountStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заявка уже обработана.");
        }
        AccountStatus oldStatus = target.getAccountStatus();
        target.setAccountStatus(newStatus);
        User savedUser = userRepository.save(target);
        UserAuditLog auditLog = new UserAuditLog();
        auditLog.setUser(target);
        auditLog.setActor(actor);
        auditLog.setAction(newStatus == AccountStatus.ACCEPTED ? "APPROVED" : "REJECTED");
        auditLog.setOldAccountStatus(oldStatus);
        auditLog.setNewAccountStatus(newStatus);
        auditLog.setOldIsBlocked(target.isBlocked());
        auditLog.setNewIsBlocked(target.isBlocked());
        auditLog.setSource(AuditSource.USER);
        userAuditLogRepository.save(auditLog);
        return savedUser;
    }

    /**
     * Проверяет, может ли администратор обработать заявку:
     * подтвердить или отклонить её.
     * Для этой операции действуют отдельные правила доступа.
     * Они не расширяют полномочия на блокировку аккаунтов
     * и восстановление отклонённых заявок.
     */
    private boolean canManage(User actor, User target) {
        return actor.getAccountStatus() == AccountStatus.ACCEPTED
                && !actor.isBlocked()
                && target.getAccountStatus() == AccountStatus.PENDING
                && hasApprovalScope(actor, target);
    }

    /**
     * Проверяет полномочия администратора именно на подтверждение
     * или отклонение заявки.
     */
    private boolean hasApprovalScope(User actor, User target) {
        RoleCode actorRole = actor.getRole().getCode();
        RoleCode targetRole = target.getRole().getCode();

        return switch (actorRole) {
            // Администратор региона:
            // подтверждает администраторов МСУ и администраторов ОО
            // только в пределах своего региона.
            case REGION_ADMIN -> (targetRole == RoleCode.MSU_ADMIN || targetRole == RoleCode.OO_ADMIN) && sameRegion(actor, target);

            // Администратор МСУ:
            // подтверждает администраторов ОО и наставников
            // только в пределах своего МСУ.
            case MSU_ADMIN -> (targetRole == RoleCode.OO_ADMIN || targetRole == RoleCode.MENTOR) && sameMsu(actor, target);

            // Администратор ОО:
            // подтверждает наставляемых своей организации.
            case OO_ADMIN -> targetRole == RoleCode.MENTEE && sameOrganization(actor, target);
            default -> false;
        };
    }

    private boolean hasManagementScope(User actor, User target) {
        RoleCode actorRole = actor.getRole().getCode();
        RoleCode targetRole = target.getRole().getCode();

        return switch (actorRole) {
            case REGION_ADMIN -> (targetRole == RoleCode.MSU_ADMIN || targetRole == RoleCode.OO_ADMIN) && sameRegion(actor, target);
            case MSU_ADMIN -> (targetRole == RoleCode.OO_ADMIN || targetRole == RoleCode.MENTOR) && sameMsu(actor, target);
            case OO_ADMIN -> targetRole == RoleCode.MENTEE && sameOrganization(actor, target);
            default -> false;
        };
    }

    private boolean sameRegion(User actor, User target) {
        return actor.getRegion() != null && target.getRegion() != null && actor.getRegion().getId().equals(target.getRegion().getId());
    }

    private boolean sameMsu(User actor, User target) {
        return actor.getMsu() != null
                && target.getMsu() != null
                && actor.getMsu()
                    .getId()
                    .equals(target.getMsu().getId());
    }

    private boolean sameOrganization(User actor, User target) {
        return actor.getEducationalOrganization() != null
                && target.getEducationalOrganization() != null
                && actor.getEducationalOrganization()
                    .getId()
                    .equals(target.getEducationalOrganization().getId());
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден: " + id));
    }
}
