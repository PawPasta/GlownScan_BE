package com.pawpasta.glowscan_be.repository;

import com.pawpasta.glowscan_be.modal.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Short> {
    Optional<Role> findByCode(String code);
}
