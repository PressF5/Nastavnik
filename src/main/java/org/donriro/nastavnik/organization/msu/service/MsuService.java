package org.donriro.nastavnik.organization.msu.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.organization.msu.dto.request.CreateMsuRequest;
import org.donriro.nastavnik.organization.msu.entity.Msu;
import org.donriro.nastavnik.organization.msu.repository.MsuRepository;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.repository.UserRepository;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MsuService {

    private final MsuRepository msuRepository;
    private final UserRepository userRepository;

    @Transactional
    public Msu create(Long actorId, CreateMsuRequest request) {
        User actor = getUser(actorId);
        checkRegionAdmin(actor);

        if (msuRepository.existsByRegionIdAndCode(actor.getRegion().getId(), request.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "МСУ с таким кодом уже существует в регионе.");
        }

        Msu msu = new Msu();
        msu.setCode(request.code());
        msu.setName(request.name().trim());

        // МСУ автоматически относится к региону текущего администратора.
        msu.setRegion(actor.getRegion());

        return msuRepository.save(msu);
    }

    private void checkRegionAdmin(User actor) {
        if (actor.getAccountStatus() != AccountStatus.ACCEPTED
                || actor.isBlocked()
                || actor.getRole().getCode() != RoleCode.REGION_ADMIN
                || actor.getRegion() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Создавать МСУ может только действующий администратор региона.");
        }
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден: " + id));
    }
}
