package com.pawpasta.glowscan_be.repository;

import com.pawpasta.glowscan_be.modal.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmailAndDeletedAtIsNull(String email);

    List<User> findByEmail(String email);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);
}
