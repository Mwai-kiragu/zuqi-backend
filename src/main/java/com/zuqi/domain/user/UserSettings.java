package com.zuqi.domain.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_settings")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // Email Notifications
    @Column(name = "email_notifications_enabled")
    @Builder.Default
    private boolean emailNotificationsEnabled = true;

    // Push Notifications
    @Column(name = "push_notifications_enabled")
    @Builder.Default
    private boolean pushNotificationsEnabled = true;

    // Order Updates
    @Column(name = "order_updates_enabled")
    @Builder.Default
    private boolean orderUpdatesEnabled = true;

    // Payment Alerts
    @Column(name = "payment_alerts_enabled")
    @Builder.Default
    private boolean paymentAlertsEnabled = true;

    // Inventory Alerts
    @Column(name = "inventory_alerts_enabled")
    @Builder.Default
    private boolean inventoryAlertsEnabled = true;

    // Marketing Emails
    @Column(name = "marketing_emails_enabled")
    @Builder.Default
    private boolean marketingEmailsEnabled = false;

    // System Announcements
    @Column(name = "system_announcements_enabled")
    @Builder.Default
    private boolean systemAnnouncementsEnabled = true;

    // Additional preferences stored as JSON for flexibility
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String additionalPreferences;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
