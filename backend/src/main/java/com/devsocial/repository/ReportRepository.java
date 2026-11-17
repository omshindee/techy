package com.devsocial.repository;

import com.devsocial.model.Report;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {
    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, Report.TargetType type, Long targetId);

    @EntityGraph(attributePaths = {"reporter", "resolvedBy"})
    List<Report> findByStatusOrderByCreatedAtDesc(Report.Status status, Pageable pageable);

    @EntityGraph(attributePaths = {"reporter", "resolvedBy"})
    List<Report> findByStatusNotOrderByResolvedAtDesc(Report.Status status, Pageable pageable);

    List<Report> findByTargetTypeAndTargetIdAndStatus(Report.TargetType type, Long targetId, Report.Status status);

    long countByStatus(Report.Status status);
}
