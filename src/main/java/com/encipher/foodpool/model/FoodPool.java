package com.encipher.foodpool.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "food_pools")
@CompoundIndex(name = "employee_date_idx", def = "{'employeeId': 1, 'date': 1}", unique = true)
public class FoodPool {
    @Id
    private String id;
    
    private String employeeId;
    private String employeeName;
    private String employeeEmail;
    
    private String foodType; // "veg" or "nonveg"
    
    private LocalDate date;
    private LocalDateTime timestamp;
    
    public FoodPool(String employeeId, String employeeName, String employeeEmail, 
                    String foodType, LocalDate date) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.employeeEmail = employeeEmail;
        this.foodType = foodType;
        this.date = date;
        this.timestamp = LocalDateTime.now();
    }
}
