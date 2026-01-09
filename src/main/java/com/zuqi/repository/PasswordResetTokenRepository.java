package com.zuqi.repository;

import com.zuqi.domain.user.PasswordResetToken;
import com.zuqi.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for PasswordResetToken entity operations.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /**
     * Find a valid (not used, not expired) token by token string.
     */
    @Query("SELECT t FROM PasswordResetToken t WHERE t.token = :token AND t.used = false AND t.expiresAt > :now")
    Optional<PasswordResetToken> findValidToken(@Param("token") String token, @Param("now") LocalDateTime now);

    /**
     * Find token by token string.
     */
    Optional<PasswordResetToken> findByToken(String token);

    /**
     * Delete all expired tokens (cleanup).
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :now")
    void deleteExpiredTokens(@Param("now") LocalDateTime now);

    /**
     * Delete all tokens for a user (when password is successfully reset).
     */
    @Modifying
    void deleteByUser(User user);

    /**
     * Check if user has a valid (not expired, not used) token.
     */
    @Query("SELECT COUNT(t) > 0 FROM PasswordResetToken t WHERE t.user = :user AND t.used = false AND t.expiresAt > :now")
    boolean hasValidToken(@Param("user") User user, @Param("now") LocalDateTime now);
}
