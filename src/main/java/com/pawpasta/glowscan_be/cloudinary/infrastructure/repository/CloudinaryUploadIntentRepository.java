package com.pawpasta.glowscan_be.cloudinary.infrastructure.repository;

import com.pawpasta.glowscan_be.cloudinary.domain.CloudinaryUploadIntent;
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
public interface CloudinaryUploadIntentRepository extends JpaRepository<CloudinaryUploadIntent, UUID> {

    @Modifying
    @Query("""
            update CloudinaryUploadIntent intent
            set intent.revokedAt = :revokedAt
            where intent.user.id = :userId
              and intent.consumedAt is null
              and intent.revokedAt is null
            """)
    void revokePendingByUserId(@Param("userId") UUID userId, @Param("revokedAt") OffsetDateTime revokedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select intent from CloudinaryUploadIntent intent
            where intent.id = :id and intent.user.id = :userId
            """)
    Optional<CloudinaryUploadIntent> findByIdAndUserIdForUpdate(
            @Param("id") UUID id,
            @Param("userId") UUID userId
    );
}
