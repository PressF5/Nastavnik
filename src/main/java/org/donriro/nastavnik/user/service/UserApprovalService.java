package org.donriro.nastavnik.user.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.entity.UserAuditLog;
import org.donriro.nastavnik.user.repository.UserAuditLogRepository;
import org.donriro.nastavnik.user.repository.UserRepository;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.donriro.nastavnik.user.status.AuditSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    private boolean canManage(User actor, User target) {
        if (actor.getAccountStatus() != AccountStatus.ACCEPTED || actor.isBlocked() || target.getAccountStatus() != AccountStatus.PENDING) {
            return false;
        }
        RoleCode actorRole = actor.getRole().getCode();
        RoleCode targetRole = target.getRole().getCode();
        return switch (actorRole) {
            case REGION_ADMIN -> targetRole == RoleCode.MSU_ADMIN && sameRegion(actor, target);
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
