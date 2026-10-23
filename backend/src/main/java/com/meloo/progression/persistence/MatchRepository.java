package com.meloo.progression.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MatchRepository extends JpaRepository<MatchEntity, UUID> {}
