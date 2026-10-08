package com.pawpasta.glowscan_be.skinanalysis.application;

import com.pawpasta.glowscan_be.skinanalysis.domain.AnalysisOutboxEvent;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.AnalysisOutboxStatus;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.AnalysisStatus;
import com.pawpasta.glowscan_be.skinanalysis.infrastructure.repository.AnalysisOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalysisOutboxClaimService {

    private final AnalysisOutboxEventRepository outboxEventRepository;

    @Value("${app.skin-analysis.worker.batch-size}")
    private int batchSize;

    @Transactional
    public List<UUID> claimReadyEvents() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<AnalysisOutboxEvent> events = outboxEventRepository.findReadyForProcessing(
                AnalysisOutboxStatus.PENDING,
                now,
                PageRequest.of(0, batchSize)
        );
        for (AnalysisOutboxEvent event : events) {
            event.setStatus(AnalysisOutboxStatus.PROCESSING);
            event.setLockedAt(now);
            event.getSession().setStatus(AnalysisStatus.RUNNING);
            event.getSession().setStartedAt(now);
            event.getSession().setNextAttemptAt(null);
        }
        return events.stream().map(AnalysisOutboxEvent::getId).toList();
    }
}
