package com.zuqi.repository;

import com.zuqi.domain.procurement.PoConfirmationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PoConfirmationTokenRepository extends JpaRepository<PoConfirmationToken, UUID> {

    Optional<PoConfirmationToken> findByToken(String token);

    @Modifying
    @Query("DELETE FROM PoConfirmationToken t WHERE t.po.id = :poId AND t.usedAt IS NULL")
    void deleteUnusedByPoId(UUID poId);
}
