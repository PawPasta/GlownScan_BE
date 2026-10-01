package com.pawpasta.glowscan_be.profile.infrastructure;

import com.pawpasta.glowscan_be.profile.domain.SkinProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SkinProfileRepository extends JpaRepository<SkinProfile, UUID> {

    Optional<SkinProfile> findByUserProfile_User_Id(UUID userId);
}
