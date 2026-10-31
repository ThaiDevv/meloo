package com.meloo.identity.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

  /**
   * Checks whether a user exists with the specified ID and status.
   *
   * @param id the user ID
   * @param status the user status
   * @return {@code true} if a record exists matching both ID and status, {@code false} otherwise
   */
  boolean existsByIdAndStatus(UUID id, UserStatus status);
}
