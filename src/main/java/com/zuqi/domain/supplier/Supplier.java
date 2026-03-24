package com.zuqi.domain.supplier;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.merchant.KycStatus;
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
@Table(name = "suppliers", indexes = {
        @Index(name = "idx_suppliers_distributor", columnList = "distributor_id"),
        @Index(name = "idx_suppliers_category", columnList = "category_id"),
        @Index(name = "idx_suppliers_active", columnList = "active"),
        @Index(name = "idx_suppliers_supplier_code", columnList = "supplier_code"),
        @Index(name = "idx_suppliers_kra_pin", columnList = "kra_pin"),
        @Index(name = "idx_suppliers_blacklisted", columnList = "blacklisted")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "supplier_code", length = 20, nullable = false, unique = true)
    private String supplierCode;

    @Column(nullable = false)
    private String name;

    @Column(name = "kra_pin", length = 20, unique = true)
    private String kraPin;

    @Column(name = "registration_number", length = 50)
    private String registrationNumber;

    private String email;

    @Column(nullable = false)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    private String city;

    @Column(length = 100)
    private String county;

    @Column(name = "sub_county", length = 100)
    private String subCounty;

    // Bank details
    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "bank_branch", length = 100)
    private String bankBranch;

    @Column(name = "bank_account_number", length = 50)
    private String bankAccountNumber;

    @Column(name = "bank_account_name", length = 100)
    private String bankAccountName;

    @Column(name = "swift_code", length = 20)
    private String swiftCode;

    @Column(name = "payment_terms_days")
    @Builder.Default
    private Integer paymentTermsDays = 30;

    @Column(name = "credit_limit", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contact_persons", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> contactPersons = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private SupplierCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id")
    private Distributor distributor;

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
