package com.zuqi.domain.accesscontrol;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "user_type_permissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_type_id", "module"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTypePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_type_id", nullable = false)
    private UserType userType;

    @Column(nullable = false, length = 60)
    private String module;

    @Column(name = "can_create", nullable = false)
    @Builder.Default
    private boolean canCreate = false;

    @Column(name = "can_read", nullable = false)
    @Builder.Default
    private boolean canRead = false;

    @Column(name = "can_update", nullable = false)
    @Builder.Default
    private boolean canUpdate = false;

    @Column(name = "can_delete", nullable = false)
    @Builder.Default
    private boolean canDelete = false;

    @Column(name = "can_approve", nullable = false)
    @Builder.Default
    private boolean canApprove = false;

    /** When true, CREATE actions by this UserType on this module are routed through approval. */
    @Column(name = "requires_approval", nullable = false)
    @Builder.Default
    private boolean requiresApproval = false;
}
