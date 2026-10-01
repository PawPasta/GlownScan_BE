package com.pawpasta.glowscan_be.auth.infrastructure.repository;

import com.pawpasta.glowscan_be.auth.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Short> {

}
