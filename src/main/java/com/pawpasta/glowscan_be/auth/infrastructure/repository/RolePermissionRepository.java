package com.pawpasta.glowscan_be.auth.infrastructure.repository;

import com.pawpasta.glowscan_be.auth.domain.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

}
