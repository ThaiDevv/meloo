package com.meloo.chat.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ChatReportRepository extends JpaRepository<ChatReport, UUID> {}
