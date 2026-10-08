package com.pawpasta.glowscan_be.skinanalysis.infrastructure.repository;

import com.pawpasta.glowscan_be.skinanalysis.domain.AnalysisOutboxEvent;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.AnalysisOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AnalysisOutboxEventRepository extends JpaRepository<AnalysisOutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event from AnalysisOutboxEvent event
            where event.status = :status
              and event.availableAt <= :now
            order by event.availableAt asc, event.createdAt asc
            """)
    List<AnalysisOutboxEvent> findReadyForProcessing(
            AnalysisOutboxStatus status,
            OffsetDateTime now,
            Pageable pageable
    );
}
