package com.meloo.catalog.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface PlaylistTrackRepository extends JpaRepository<PlaylistTrack, PlaylistTrackId> {}
