package com.pawpasta.glowscan_be.auth.infrastructure.repository;

import com.pawpasta.glowscan_be.auth.domain.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.pawpasta.glowscan_be.auth.domain.User;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {

    Optional<UserDevice> findByUserAndDeviceUuid(User user, String deviceUuid);
}
