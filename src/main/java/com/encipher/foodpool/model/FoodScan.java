package com.encipher.foodpool.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "food_scans")
@CompoundIndexes({
    @CompoundIndex(name = "employee_date_scan_idx", def = "{'employeeId': 1, 'date': 1}", unique = true),
    @CompoundIndex(name = "employee_fooddate_scan_idx", def = "{'employeeId': 1, 'foodDate': 1}")
})
public class FoodScan {
    @Id
    private String id;
    
    private String employeeId;
    private String employeeName;
    private String foodType;
    
    private LocalDate date;         // Scan date (when food was collected)
    private LocalDate foodDate;     // Food date (same as scan date, but explicitly set)
    private LocalDateTime scanTime;
    
    private String qrData;
    
    public FoodScan(String employeeId, String employeeName, String foodType, 
                    LocalDate date, String qrData) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.foodType = foodType;
        this.date = date;
        this.foodDate = date;
        this.scanTime = LocalDateTime.now();
        this.qrData = qrData;
    }
}
