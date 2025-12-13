package com.encipher.foodpool.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "menu_config")
public class MenuConfig {
    @Id
    private String id;
    
    @Indexed(unique = true)
    private LocalDate date;
    
    // Is food available today at all?
    private boolean foodAvailable;
    
    // Is pool open for registration?
    private boolean poolOpen;
    
    private boolean vegAvailable;
    private boolean nonvegAvailable;
    
    private List<String> vegItems;
    private List<String> nonvegItems;
    
    private LocalDateTime poolOpenedAt;
    private LocalDateTime poolClosedAt;
    private LocalDateTime poolAutoCloseAt;  // Auto close time
    private int poolDurationHours;  // Duration in hours (1-24)
    private String poolOpenedBy;
    
    // Notification tracking
    private boolean notificationPoolStarted;
    private boolean notification1HourBefore;
    private boolean notification50Percent;
    private boolean notification70Percent;
    
    private LocalDateTime updatedAt;
    private String updatedBy;
    
    public MenuConfig(LocalDate date) {
        this.date = date;
        this.foodAvailable = false;  // Default: no food until admin enables
        this.poolOpen = false;
        this.vegAvailable = false;
        this.nonvegAvailable = false;
        this.vegItems = new ArrayList<>();
        this.nonvegItems = new ArrayList<>();
        this.poolDurationHours = 6;  // Default 6 hours
        this.notificationPoolStarted = false;
        this.notification1HourBefore = false;
        this.notification50Percent = false;
        this.notification70Percent = false;
        this.updatedAt = LocalDateTime.now();
    }
}
