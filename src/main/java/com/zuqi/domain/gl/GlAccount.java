package com.zuqi.domain.gl;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "gl_accounts", indexes = {
        @Index(name = "idx_gl_accounts_distributor", columnList = "distributor_id"),
        @Index(name = "idx_gl_accounts_parent",      columnList = "parent_id"),
        @Index(name = "idx_gl_accounts_type",        columnList = "account_type"),
        @Index(name = "idx_gl_accounts_active",      columnList = "active")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "distributor_id", nullable = false)
    private UUID distributorId;

    @Column(name = "account_code", length = 20, nullable = false)
    private String accountCode;

    @Column(name = "account_name", length = 200, nullable = false)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", length = 30, nullable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_sub_type", length = 40, nullable = false)
    private AccountSubType accountSubType;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_balance", length = 10, nullable = false)
    private NormalBalance normalBalance;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "level", nullable = false)
    @Builder.Default
    private int level = 1;

    @Column(name = "is_posting_account", nullable = false)
    @Builder.Default
    private boolean isPostingAccount = true;

    @Column(name = "is_system_account", nullable = false)
    @Builder.Default
    private boolean isSystemAccount = false;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
