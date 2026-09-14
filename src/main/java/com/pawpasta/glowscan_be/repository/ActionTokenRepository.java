package com.pawpasta.glowscan_be.repository;

import com.pawpasta.glowscan_be.modal.entity.ActionToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ActionTokenRepository extends JpaRepository<ActionToken, UUID> {

}
