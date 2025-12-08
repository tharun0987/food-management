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
    
    private boolean isAdmin;
    
    private boolean isActive;
    
    public Employee(String employeeId, String name, String email, LocalDate dateOfJoining) {
        this.employeeId = employeeId;
        this.name = name;
        this.email = email;
        this.dateOfJoining = dateOfJoining;
        this.isAdmin = false;
        this.isActive = true;
    }
}
