package com.pawpasta.glowscan_be.skinanalysis.infrastructure.repository;

import com.pawpasta.glowscan_be.skinanalysis.domain.AnalysisUploadIntent;
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
public interface AnalysisUploadIntentRepository extends JpaRepository<AnalysisUploadIntent, UUID> {

    @Modifying
    @Query("""
            update AnalysisUploadIntent intent
            set intent.revokedAt = :revokedAt
            where intent.user.id = :userId
              and intent.consumedAt is null
              and intent.revokedAt is null
            """)
    void revokePendingByUserId(@Param("userId") UUID userId, @Param("revokedAt") OffsetDateTime revokedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select intent from AnalysisUploadIntent intent
            where intent.id = :id and intent.user.id = :userId
            """)
    Optional<AnalysisUploadIntent> findByIdAndUserIdForUpdate(
            @Param("id") UUID id,
            @Param("userId") UUID userId
    );
}
