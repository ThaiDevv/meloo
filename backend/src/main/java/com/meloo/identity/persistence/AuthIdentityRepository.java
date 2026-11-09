package com.meloo.identity.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, UUID> {
  Optional<AuthIdentity> findByProviderAndProviderSubject(
      AuthProvider provider, String providerSubject);

  List<AuthIdentity> findAllByUserId(UUID userId);

  List<AuthIdentity> findAllByUserIdIn(java.util.Collection<UUID> userIds);
}
