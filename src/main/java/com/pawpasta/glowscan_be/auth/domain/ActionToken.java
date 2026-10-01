package com.pawpasta.glowscan_be.auth.domain;

import com.pawpasta.glowscan_be.auth.domain.enums.ActionTokenPurpose;
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
@Table(name = "action_tokens", schema = "app_auth")
public class ActionToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private ActionTokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime expiresAt;

    @Column(name = "consumed_at", columnDefinition = "timestamptz")
    private OffsetDateTime consumedAt;

    @Column(name = "revoked_at", columnDefinition = "timestamptz")
    private OffsetDateTime revokedAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false, columnDefinition = "timestamptz default CURRENT_TIMESTAMP")
    private OffsetDateTime createdAt;
}

