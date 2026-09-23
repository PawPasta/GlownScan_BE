package com.pawpasta.glowscan_be.audit.infrastructure.repository;

import com.pawpasta.glowscan_be.audit.domain.AuthAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLog, Long> {

}
