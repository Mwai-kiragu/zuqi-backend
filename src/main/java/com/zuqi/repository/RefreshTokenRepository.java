package com.zuqi.repository;

import com.zuqi.domain.user.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.user.id = :userId AND t.revoked = false")
    void revokeAllUserTokens(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :expiryDate")
    void deleteExpiredTokens(@Param("expiryDate") LocalDateTime expiryDate);
}
