package com.encipher.foodpool.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "audit_logs")
public class AuditLog {
    @Id
    private String id;
    
    @Indexed
    private LocalDateTime timestamp;
    
    @Indexed
    private String action;          // ROLE_CHANGE, POOL_STARTED, POOL_CLOSED_MANUAL, POOL_CLOSED_AUTO, etc.
    
    @Indexed
    private String actorEmail;      // Who performed the action
    private String actorName;
    
    private String targetEntity;    // e.g., employee ID for role change, menuConfig ID for pool actions
    private String targetName;      // Human readable target name
    
    private String oldValue;        // Previous state
    private String newValue;        // New state
    
    private String details;         // Additional info/description
    
    private boolean isAutomatic;    // True if system-triggered (e.g., auto pool close)
    
    // Action type constants
    public static final String ACTION_ROLE_CHANGE = "ROLE_CHANGE";
    public static final String ACTION_POOL_STARTED = "POOL_STARTED";
    public static final String ACTION_POOL_CLOSED_MANUAL = "POOL_CLOSED_MANUAL";
    public static final String ACTION_POOL_CLOSED_AUTO = "POOL_CLOSED_AUTO";
    public static final String ACTION_QR_GENERATED = "QR_GENERATED";
    public static final String ACTION_QR_DEACTIVATED = "QR_DEACTIVATED";
    public static final String ACTION_GRACE_REQUEST_SUBMITTED = "GRACE_REQUEST_SUBMITTED";
    public static final String ACTION_GRACE_REQUEST_APPROVED = "GRACE_REQUEST_APPROVED";
    public static final String ACTION_GRACE_REQUEST_REJECTED = "GRACE_REQUEST_REJECTED";
    public static final String ACTION_MENU_UPDATED = "MENU_UPDATED";
    public static final String ACTION_NOTIFICATION_SENT = "NOTIFICATION_SENT";
}

