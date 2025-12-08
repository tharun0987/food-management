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
    private String poolOpenedBy;
    
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
        this.updatedAt = LocalDateTime.now();
    }
}
