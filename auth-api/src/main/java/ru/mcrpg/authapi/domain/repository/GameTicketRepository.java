package ru.mcrpg.authapi.domain.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.mcrpg.authapi.domain.entity.GameTicketEntity;

public interface GameTicketRepository extends JpaRepository<GameTicketEntity, UUID> {

    Optional<GameTicketEntity> findFirstByTicketHash(String ticketHash);
}
