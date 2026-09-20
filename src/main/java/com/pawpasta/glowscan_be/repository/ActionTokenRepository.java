package com.pawpasta.glowscan_be.repository;

import com.pawpasta.glowscan_be.modal.entity.ActionToken;
import com.pawpasta.glowscan_be.modal.entity.enums.ActionTokenPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ActionTokenRepository extends JpaRepository<ActionToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select actionToken
            from ActionToken actionToken
            join fetch actionToken.user user
            where user.email = :email
              and user.deletedAt is null
              and actionToken.tokenHash = :tokenHash
              and actionToken.purpose = :purpose
              and actionToken.consumedAt is null
              and actionToken.revokedAt is null
            """)
    Optional<ActionToken> findPendingByUserEmailAndTokenHashAndPurpose(
            @Param("email") String email,
            @Param("tokenHash") String tokenHash,
            @Param("purpose") ActionTokenPurpose purpose
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            update ActionToken actionToken
            set actionToken.revokedAt = :revokedAt
            where actionToken.user.id = :userId
              and actionToken.purpose = :purpose
              and actionToken.consumedAt is null
              and actionToken.revokedAt is null
            """)
    void revokePendingByUserIdAndPurpose(
            @Param("userId") UUID userId,
            @Param("purpose") ActionTokenPurpose purpose,
            @Param("revokedAt") OffsetDateTime revokedAt
    );

}
