package com.meloo.catalog.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface GenreRepository extends JpaRepository<Genre, UUID> {}
