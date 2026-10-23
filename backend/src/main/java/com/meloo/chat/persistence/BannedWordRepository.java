package com.meloo.chat.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BannedWordRepository extends JpaRepository<BannedWord, UUID> {}
