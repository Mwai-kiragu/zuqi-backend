package com.zuqi.domain.customer;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "customers", indexes = {
        @Index(name = "idx_customers_distributor", columnList = "distributor_id"),
        @Index(name = "idx_customers_category", columnList = "category_id"),
        @Index(name = "idx_customers_sales_rep", columnList = "assigned_sales_rep_id"),
        @Index(name = "idx_customers_active", columnList = "active"),
        @Index(name = "idx_customers_customer_code", columnList = "customer_code"),
        @Index(name = "idx_customers_kra_pin", columnList = "kra_pin"),
        @Index(name = "idx_customers_blacklisted", columnList = "blacklisted")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_code", length = 20, unique = true)
    private String customerCode;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "owner_name")
    private String ownerName;

    private String email;

    @Column(nullable = false)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    private String city;

    @Column(name = "county", length = 100)
    private String county;

    @Column(name = "sub_county", length = 100)
    private String subCounty;

    @Column(name = "kra_pin", length = 20, unique = true)
    private String kraPin;

    @Column(name = "national_id", length = 20)
    private String nationalId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contact_persons", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> contactPersons = new ArrayList<>();

    private BigDecimal latitude;

    private BigDecimal longitude;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private CustomerCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id")
    private Distributor distributor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_sales_rep_id")
    private User assignedSalesRep;

    @Column(name = "route_id")
    private UUID routeId;

    @Column(name = "credit_limit", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Column(name = "current_balance", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "payment_terms_days")
    @Builder.Default
    private Integer paymentTermsDays = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean verified = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean blacklisted = false;

    @Column(name = "blacklisted_reason", length = 500)
    private String blacklistedReason;

    @Column(name = "blacklisted_at")
    private LocalDateTime blacklistedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blacklisted_by")
    private User blacklistedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", length = 20)
    @Builder.Default
    private KycStatus kycStatus = KycStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "kyc_documents", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> kycDocuments = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

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

    @Column(name = "approval_status", length = 30)
    @Builder.Default
    private String approvalStatus = "APPROVED";

    @Column(name = "created_by_id")
    private UUID createdById;
}
