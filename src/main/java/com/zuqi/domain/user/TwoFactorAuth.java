package com.zuqi.domain.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "two_factor_auth")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    @Builder.Default
    private TwoFactorType type = TwoFactorType.TOTP;

    // Secret key for TOTP (encrypted)
    @Column(name = "secret_key")
    private String secretKey;

    // Backup codes (stored as JSON array, hashed)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "backup_codes", columnDefinition = "jsonb")
    private List<String> backupCodes;

    // Number of backup codes remaining
    @Column(name = "backup_codes_remaining")
    @Builder.Default
    private Integer backupCodesRemaining = 10;

    // Phone number for SMS-based 2FA
    @Column(name = "phone_number")
    private String phoneNumber;

    // Whether 2FA setup is complete
    @Column(name = "verified")
    @Builder.Default
    private boolean verified = false;

    // Last time 2FA was used successfully
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public enum TwoFactorType {
        TOTP,       // Time-based One-Time Password (Google Authenticator, etc.)
        SMS,        // SMS-based verification
        EMAIL       // Email-based verification
    }
}
