package com.encipher.foodpool.repository;

import com.encipher.foodpool.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    
    // Find by date range
    List<AuditLog> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime start, LocalDateTime end);
    
    // Find by date range with pagination
    Page<AuditLog> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    // Find by action type
    List<AuditLog> findByActionOrderByTimestampDesc(String action);
    
    // Find by action type with date range
    List<AuditLog> findByActionAndTimestampBetweenOrderByTimestampDesc(String action, LocalDateTime start, LocalDateTime end);
    
    // Find by actor
    List<AuditLog> findByActorEmailOrderByTimestampDesc(String actorEmail);
    
    // Find by target entity
    List<AuditLog> findByTargetEntityOrderByTimestampDesc(String targetEntity);
    
    // Find recent logs
    List<AuditLog> findTop50ByOrderByTimestampDesc();
    
    // Find by actor name containing (search)
    List<AuditLog> findByActorNameContainingIgnoreCaseOrderByTimestampDesc(String actorName);
    
    // Combined filters
    Page<AuditLog> findByActionAndTimestampBetweenOrderByTimestampDesc(
            String action, LocalDateTime start, LocalDateTime end, Pageable pageable);
}

