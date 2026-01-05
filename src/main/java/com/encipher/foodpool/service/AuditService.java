package com.encipher.foodpool.service;

import com.encipher.foodpool.model.AuditLog;
import com.encipher.foodpool.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {
    
    private final AuditLogRepository auditLogRepository;
    
    /**
     * Log an action with full details
     */
    public AuditLog logAction(String action, String actorEmail, String actorName,
                              String targetEntity, String targetName,
                              String oldValue, String newValue,
                              String details, boolean isAutomatic) {
        AuditLog auditLog = AuditLog.builder()
                .timestamp(LocalDateTime.now())
                .action(action)
                .actorEmail(actorEmail)
                .actorName(actorName)
                .targetEntity(targetEntity)
                .targetName(targetName)
                .oldValue(oldValue)
                .newValue(newValue)
                .details(details)
                .isAutomatic(isAutomatic)
                .build();
        
        AuditLog saved = auditLogRepository.save(auditLog);
        log.info("Audit log created: {} by {} on {}", action, actorName, targetEntity);
        return saved;
    }
    
    /**
     * Log a simple action (convenience method)
     */
    public AuditLog logAction(String action, String actorEmail, String actorName, String details) {
        return logAction(action, actorEmail, actorName, null, null, null, null, details, false);
    }
    
    /**
     * Log an automatic/system action
     */
    public AuditLog logSystemAction(String action, String targetEntity, String targetName, String details) {
        return logAction(action, "SYSTEM", "System", targetEntity, targetName, null, null, details, true);
    }
    
    /**
     * Log role change
     */
    public AuditLog logRoleChange(String actorEmail, String actorName, 
                                   String targetEmployeeId, String targetEmployeeName,
                                   String oldRole, String newRole) {
        String details = String.format("Role changed from %s to %s", oldRole, newRole);
        return logAction(AuditLog.ACTION_ROLE_CHANGE, actorEmail, actorName,
                targetEmployeeId, targetEmployeeName, oldRole, newRole, details, false);
    }
    
    /**
     * Log pool started
     */
    public AuditLog logPoolStarted(String actorEmail, String actorName, 
                                    LocalDate foodDate, int durationHours) {
        String details = String.format("Food pool started for %s with duration %d hours", 
                foodDate, durationHours);
        return logAction(AuditLog.ACTION_POOL_STARTED, actorEmail, actorName,
                foodDate.toString(), "Food Pool", null, "OPEN", details, false);
    }
    
    /**
     * Log pool closed manually
     */
    public AuditLog logPoolClosedManual(String actorEmail, String actorName, LocalDate foodDate) {
        String details = String.format("Food pool for %s manually closed", foodDate);
        return logAction(AuditLog.ACTION_POOL_CLOSED_MANUAL, actorEmail, actorName,
                foodDate.toString(), "Food Pool", "OPEN", "CLOSED", details, false);
    }
    
    /**
     * Log pool closed automatically
     */
    public AuditLog logPoolClosedAuto(LocalDate foodDate) {
        String details = String.format("Food pool for %s automatically closed (timer expired)", foodDate);
        return logAction(AuditLog.ACTION_POOL_CLOSED_AUTO, "SYSTEM", "System",
                foodDate.toString(), "Food Pool", "OPEN", "CLOSED", details, true);
    }
    
    /**
     * Log QR code generated
     */
    public AuditLog logQrGenerated(String actorEmail, String actorName, 
                                    LocalDate foodDate, int expirationHours) {
        String details = String.format("QR code generated for %s with %d hour expiration", 
                foodDate, expirationHours);
        return logAction(AuditLog.ACTION_QR_GENERATED, actorEmail, actorName,
                foodDate.toString(), "QR Code", null, null, details, false);
    }
    
    /**
     * Log grace request approved
     */
    public AuditLog logGraceRequestApproved(String actorEmail, String actorName,
                                             String employeeId, String employeeName) {
        String details = String.format("Grace request approved for %s", employeeName);
        return logAction(AuditLog.ACTION_GRACE_REQUEST_APPROVED, actorEmail, actorName,
                employeeId, employeeName, "PENDING", "APPROVED", details, false);
    }
    
    /**
     * Log grace request rejected
     */
    public AuditLog logGraceRequestRejected(String actorEmail, String actorName,
                                             String employeeId, String employeeName, String reason) {
        String details = String.format("Grace request rejected for %s. Reason: %s", employeeName, reason);
        return logAction(AuditLog.ACTION_GRACE_REQUEST_REJECTED, actorEmail, actorName,
                employeeId, employeeName, "PENDING", "REJECTED", details, false);
    }
    
    // ============== RETRIEVAL METHODS ==============
    
    /**
     * Get recent audit logs (last 50)
     */
    public List<AuditLog> getRecentLogs() {
        return auditLogRepository.findTop50ByOrderByTimestampDesc();
    }
    
    /**
     * Get audit logs for date range
     */
    public List<AuditLog> getAuditLogs(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);
        return auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(start, end);
    }
    
    /**
     * Get audit logs with pagination
     */
    public Page<AuditLog> getAuditLogsPaginated(LocalDate startDate, LocalDate endDate, int page, int size) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);
        Pageable pageable = PageRequest.of(page, size);
        return auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(start, end, pageable);
    }
    
    /**
     * Get audit logs filtered by action type
     */
    public List<AuditLog> getAuditLogsByAction(String action, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);
        return auditLogRepository.findByActionAndTimestampBetweenOrderByTimestampDesc(action, start, end);
    }
    
    /**
     * Get audit logs for a specific entity
     */
    public List<AuditLog> getAuditLogsForEntity(String entityId) {
        return auditLogRepository.findByTargetEntityOrderByTimestampDesc(entityId);
    }
    
    /**
     * Search audit logs by actor name
     */
    public List<AuditLog> searchByActorName(String name) {
        return auditLogRepository.findByActorNameContainingIgnoreCaseOrderByTimestampDesc(name);
    }
}

