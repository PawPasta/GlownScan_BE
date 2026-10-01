package com.pawpasta.glowscan_be.notification.domain;

import com.pawpasta.glowscan_be.auth.domain.UserDevice;
import com.pawpasta.glowscan_be.notification.domain.enums.PushProvider;
import com.pawpasta.glowscan_be.notification.domain.enums.PushRegistrationStatus;
import com.pawpasta.glowscan_be.notification.domain.enums.PushRegistrationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "push_registrations", schema = "app_auth")
public class PushRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private UserDevice device;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private PushProvider provider = PushProvider.FCM;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_type", nullable = false, length = 30)
    private PushRegistrationType registrationType = PushRegistrationType.FCM_TOKEN;

    @Column(name = "registration_value", nullable = false, unique = true, columnDefinition = "text")
    private String registrationValue;

    @Column(name = "notification_enabled", nullable = false)
    private boolean notificationEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PushRegistrationStatus status = PushRegistrationStatus.ACTIVE;

    @Column(name = "last_registered_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime lastRegisteredAt = OffsetDateTime.now();

    @Column(name = "last_success_at", columnDefinition = "timestamptz")
    private OffsetDateTime lastSuccessAt;

    @Column(name = "last_failure_at", columnDefinition = "timestamptz")
    private OffsetDateTime lastFailureAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false, columnDefinition = "timestamptz default CURRENT_TIMESTAMP")
    private OffsetDateTime createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false, columnDefinition = "timestamptz default CURRENT_TIMESTAMP")
    private OffsetDateTime updatedAt;
}

