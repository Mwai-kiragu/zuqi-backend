package com.zuqi.domain.mpesa;

import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mpesa_configs", indexes = {
        @Index(name = "idx_mpesa_configs_merchant_id", columnList = "merchant_id"),
        @Index(name = "idx_mpesa_configs_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MpesaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "business_name", nullable = false, length = 255)
    private String businessName;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private MpesaTransactionType transactionType;

    @Column(name = "business_short_code", nullable = false, length = 20)
    private String businessShortCode;

    @Column(name = "till_number", length = 20)
    private String tillNumber;

    @Column(name = "store_number", length = 20)
    private String storeNumber;

    @Column(name = "ho_number", length = 20)
    private String hoNumber;

    @Column(name = "business_no", length = 20)
    private String businessNo;

    @Column(name = "account_reference", length = 100)
    private String accountReference;

    // Stored encrypted by the external service — keep opaque
    @Column(name = "consumer_key", columnDefinition = "TEXT")
    private String consumerKey;

    @Column(name = "consumer_secret", columnDefinition = "TEXT")
    private String consumerSecret;

    @Column(name = "pass_key", columnDefinition = "TEXT")
    private String passKey;

    @Column(name = "third_party_callback", length = 500)
    private String thirdPartyCallback;

    /** The _id returned by the external daraja service after activation */
    @Column(name = "external_id", length = 100)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MpesaConfigStatus status = MpesaConfigStatus.ACTIVE;

    @Column(name = "terms_accepted", nullable = false)
    @Builder.Default
    private boolean termsAccepted = true;

    /** The user who activated this configuration */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "configured_by_id")
    private User configuredBy;

    /** Denormalized full name — survives user deletion */
    @Column(name = "configured_by_name", length = 255)
    private String configuredByName;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
