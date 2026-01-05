package com.encipher.foodpool.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "grace_requests")
public class GraceRequest {
    @Id
    private String id;
    
    @Indexed
    private String employeeId;
    private String employeeName;
    private String employeeEmail;
    
    @Indexed
    private LocalDate foodDate;         // The food date they want to vote for
    
    private String foodType;            // veg or nonveg
    private String reason;              // Optional reason for late vote
    
    private LocalDateTime requestedAt;
    
    @Indexed
    private String status;              // PENDING, APPROVED, REJECTED
    
    private String reviewedBy;          // Admin who approved/rejected (email)
    private String reviewedByName;      // Admin name
    private LocalDateTime reviewedAt;
    private String rejectionReason;     // Reason for rejection (if applicable)
    
    // Status constants
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    
    /**
     * Check if request is pending
     */
    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }
    
    /**
     * Check if request is approved
     */
    public boolean isApproved() {
        return STATUS_APPROVED.equals(status);
    }
    
    /**
     * Check if request is rejected
     */
    public boolean isRejected() {
        return STATUS_REJECTED.equals(status);
    }
}

