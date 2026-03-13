package com.zuqi.domain.kcb;

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
@Table(name = "kcb_configs", indexes = {
        @Index(name = "idx_kcb_configs_merchant_id", columnList = "merchant_id"),
        @Index(name = "idx_kcb_configs_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KcbConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "business_name", nullable = false, length = 255)
    private String businessName;

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    @Column(name = "kcb_account_type", length = 50)
    private String kcbAccountType;

    @Column(name = "business_no", length = 50)
    private String businessNo;

    @Column(name = "account_type", length = 50)
    private String accountType;

    @Column(name = "is_subscription_account", nullable = false)
    @Builder.Default
    private boolean subscriptionAccount = false;

    @Column(name = "third_party_callback", length = 500)
    private String thirdPartyCallback;

    @Column(name = "consumer_key", columnDefinition = "TEXT")
    private String consumerKey;

    @Column(name = "consumer_secret", columnDefinition = "TEXT")
    private String consumerSecret;

    @Column(name = "pass_key", columnDefinition = "TEXT")
    private String passKey;

    /** The _id returned by the ZED/KCB service after activation */
    @Column(name = "external_id", length = 100)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private KcbConfigStatus status = KcbConfigStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "configured_by_id")
    private User configuredBy;

    @Column(name = "configured_by_name", length = 255)
    private String configuredByName;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
