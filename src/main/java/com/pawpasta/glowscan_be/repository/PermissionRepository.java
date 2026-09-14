package com.pawpasta.glowscan_be.repository;

import com.pawpasta.glowscan_be.modal.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Short> {

}
