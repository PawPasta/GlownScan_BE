package com.pawpasta.glowscan_be.repository;

import com.pawpasta.glowscan_be.modal.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken refreshToken
            set refreshToken.revokedAt = :revokedAt,
                refreshToken.revokeReason = :revokeReason
            where refreshToken.user.id = :userId
              and refreshToken.revokedAt is null
            """)
    void revokeActiveTokensByUserId(
            @Param("userId") UUID userId,
            @Param("revokedAt") OffsetDateTime revokedAt,
            @Param("revokeReason") String revokeReason
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken refreshToken
            set refreshToken.revokedAt = :revokedAt,
                refreshToken.revokeReason = :revokeReason
            where refreshToken.tokenFamilyId = :tokenFamilyId
              and refreshToken.revokedAt is null
            """)
    void revokeActiveTokensByFamilyId(
            @Param("tokenFamilyId") UUID tokenFamilyId,
            @Param("revokedAt") OffsetDateTime revokedAt,
            @Param("revokeReason") String revokeReason
    );
}
