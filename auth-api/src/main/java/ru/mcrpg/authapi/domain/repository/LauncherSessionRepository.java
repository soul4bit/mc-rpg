package ru.mcrpg.authapi.domain.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.mcrpg.authapi.domain.entity.LauncherSessionEntity;

public interface LauncherSessionRepository extends JpaRepository<LauncherSessionEntity, UUID> {

    Optional<LauncherSessionEntity> findFirstByRefreshTokenHash(String refreshTokenHash);
}
