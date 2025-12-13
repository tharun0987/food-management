package com.encipher.foodpool.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "employees")
public class Employee {
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String employeeId;
    
    private String name;
    
    @Indexed
    private String email;
    
    private LocalDate dateOfJoining;
    
    // Role: ADMINISTRATOR, CONTRIBUTOR, USER
    private String role;
    
    // Legacy field - kept for backward compatibility
    private boolean isAdmin;
    
    private boolean isActive;
    
    public Employee(String employeeId, String name, String email, LocalDate dateOfJoining) {
        this.employeeId = employeeId;
        this.name = name;
        this.email = email;
        this.dateOfJoining = dateOfJoining;
        this.role = "USER";
        this.isAdmin = false;
        this.isActive = true;
    }
    
    // Helper methods for role checking
    public boolean isAdministrator() {
        return "ADMINISTRATOR".equals(role) || isAdmin;
    }
    
    public boolean isContributor() {
        return "CONTRIBUTOR".equals(role);
    }
    
    public boolean hasAdminAccess() {
        return isAdministrator() || isContributor();
    }
}
