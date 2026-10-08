package com.pawpasta.glowscan_be.skinanalysis.infrastructure.repository;

import com.pawpasta.glowscan_be.skinanalysis.domain.AssessmentSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AssessmentSnapshotRepository extends JpaRepository<AssessmentSnapshot, UUID> {
}
