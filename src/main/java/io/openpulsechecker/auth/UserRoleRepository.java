package io.openpulsechecker.auth;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UserRoleEntity.Key> {
    List<UserRoleEntity> findByUserId(UUID userId);
}
