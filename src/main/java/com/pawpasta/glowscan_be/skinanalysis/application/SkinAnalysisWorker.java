package com.pawpasta.glowscan_be.skinanalysis.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SkinAnalysisWorker {

    private final AnalysisOutboxClaimService claimService;
    private final SkinAnalysisProcessingService processingService;

    @Scheduled(fixedDelayString = "${app.skin-analysis.worker.fixed-delay}")
    public void dispatch() {
        claimService.claimReadyEvents().forEach(processingService::process);
    }
}
