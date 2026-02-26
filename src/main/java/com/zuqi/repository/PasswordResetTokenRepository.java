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

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    @Query("SELECT t FROM PasswordResetToken t WHERE t.token = :token AND t.used = false AND t.expiresAt > :now")
    Optional<PasswordResetToken> findValidToken(@Param("token") String token, @Param("now") LocalDateTime now);

    Optional<PasswordResetToken> findByToken(String token);

    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :now")
    void deleteExpiredTokens(@Param("now") LocalDateTime now);

    @Modifying
    void deleteByUser(User user);

    @Query("SELECT COUNT(t) > 0 FROM PasswordResetToken t WHERE t.user = :user AND t.used = false AND t.expiresAt > :now")
    boolean hasValidToken(@Param("user") User user, @Param("now") LocalDateTime now);

    @Query("SELECT t FROM PasswordResetToken t WHERE t.user.email = :email AND t.token = :otp AND t.used = false AND t.expiresAt > :now")
    Optional<PasswordResetToken> findValidOtpByEmailAndCode(@Param("email") String email, @Param("otp") String otp, @Param("now") LocalDateTime now);

    @Query("SELECT t FROM PasswordResetToken t WHERE t.user.email = :email AND t.token = :otp AND t.used = false AND t.expiresAt > :now AND t.purpose = :purpose")
    Optional<PasswordResetToken> findValidOtpByEmailAndCodeAndPurpose(@Param("email") String email, @Param("otp") String otp, @Param("now") LocalDateTime now, @Param("purpose") com.zuqi.domain.user.TokenPurpose purpose);
}
