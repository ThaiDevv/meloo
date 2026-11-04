package com.meloo.chat.persistence;

import com.meloo.chat.api.ReportStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatReportRepository extends JpaRepository<ChatReport, UUID> {

  List<ChatReport> findByStatusOrderByCreatedAtDesc(ReportStatus status);

  List<ChatReport> findAllByOrderByCreatedAtDesc();
}
