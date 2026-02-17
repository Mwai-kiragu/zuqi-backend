package com.zuqi.repository;

import com.zuqi.domain.user.TwoFactorAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TwoFactorAuthRepository extends JpaRepository<TwoFactorAuth, UUID> {

    Optional<TwoFactorAuth> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    @Modifying
    @Query("UPDATE TwoFactorAuth t SET t.lastUsedAt = :lastUsedAt WHERE t.user.id = :userId")
    void updateLastUsedAt(@Param("userId") UUID userId, @Param("lastUsedAt") LocalDateTime lastUsedAt);

    @Modifying
    @Query("UPDATE TwoFactorAuth t SET t.backupCodesRemaining = t.backupCodesRemaining - 1 WHERE t.user.id = :userId")
    void decrementBackupCodesRemaining(@Param("userId") UUID userId);
}
