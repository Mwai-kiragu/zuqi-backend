package com.zuqi.domain.distributor;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.zuqi.domain.customer.KycStatus;
import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.user.User;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "distributors", indexes = {
        @Index(name = "idx_distributors_active", columnList = "active")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Distributor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "registration_number")
    private String registrationNumber;

    private String email;

    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    private String city;

    @Column(nullable = false)
    @Builder.Default
    private String country = "Kenya";

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> settings = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", length = 20)
    @Builder.Default
    private KycStatus kycStatus = KycStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "kyc_documents", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> kycDocuments = new HashMap<>();

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deactivation_reason", length = 500)
    private String deactivationReason;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deactivated_by")
    private User deactivatedBy;
}
