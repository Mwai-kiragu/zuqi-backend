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

/**
 * Repository for RefreshToken entity operations.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Find a valid (non-revoked) refresh token by its value.
     *
     * @param token the token value
     * @return an Optional containing the token if found
     */
    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);

    /**
     * Revoke all refresh tokens for a user.
     *
     * @param userId the user ID
     */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.user.id = :userId AND t.revoked = false")
    void revokeAllUserTokens(@Param("userId") UUID userId);

    /**
     * Delete expired tokens for cleanup.
     *
     * @param expiryDate tokens expired before this date will be deleted
     */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :expiryDate")
    void deleteExpiredTokens(@Param("expiryDate") LocalDateTime expiryDate);
}
