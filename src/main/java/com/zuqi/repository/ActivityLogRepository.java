package com.zuqi.repository;

import com.zuqi.domain.audit.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID>,
        JpaSpecificationExecutor<ActivityLog> {

    Page<ActivityLog> findByUserId(UUID userId, Pageable pageable);

    List<ActivityLog> findByEntityTypeAndEntityId(String entityType, UUID entityId);

    Page<ActivityLog> findByEntityTypeAndEntityId(String entityType, UUID entityId, Pageable pageable);

    long countByUserIdAndCreatedAtBetween(UUID userId, LocalDateTime from, LocalDateTime to);

    long countByEntityTypeAndEntityId(String entityType, UUID entityId);
}
