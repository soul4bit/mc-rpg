package ru.mcrpg.authapi.domain.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.mcrpg.authapi.domain.entity.AccountEntity;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    Optional<AccountEntity> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);
}
