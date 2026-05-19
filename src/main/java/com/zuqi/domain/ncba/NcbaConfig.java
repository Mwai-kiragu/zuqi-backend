package com.zuqi.domain.ncba;

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
@Table(name = "ncba_configs", indexes = {
        @Index(name = "idx_ncba_configs_merchant_id", columnList = "merchant_id"),
        @Index(name = "idx_ncba_configs_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NcbaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "business_name", nullable = false, length = 255)
    private String businessName;

    @Column(name = "paybill_no", nullable = false, length = 50)
    private String paybillNo;

    @Column(name = "network", length = 50)
    private String network;

    /** lookup_id returned by the NCBA payment service after configuration */
    @Column(name = "lookup_id", length = 100)
    private String lookupId;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private NcbaConfigStatus status = NcbaConfigStatus.ACTIVE;

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
