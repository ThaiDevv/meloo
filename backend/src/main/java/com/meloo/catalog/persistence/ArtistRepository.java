package com.meloo.catalog.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ArtistRepository extends JpaRepository<Artist, UUID> {}
