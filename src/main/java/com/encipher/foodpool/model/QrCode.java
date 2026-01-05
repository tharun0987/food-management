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
@Document(collection = "qr_codes")
public class QrCode {
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String token;           // Unique token (UUID)
    
    @Indexed
    private LocalDate foodDate;     // Which food date this QR is for
    
    private LocalDateTime generatedAt;
    
    @Indexed
    private LocalDateTime expiresAt;
    
    private String generatedBy;     // Admin/Contributor email who generated
    private String generatedByName; // Admin/Contributor name
    
    @Indexed
    private boolean isActive;       // Can be manually deactivated
    
    private String deactivatedBy;   // Who deactivated (if applicable)
    private LocalDateTime deactivatedAt;
    
    /**
     * Check if this QR code is currently valid
     */
    public boolean isValid() {
        if (!isActive) return false;
        if (expiresAt == null) return true;
        return LocalDateTime.now().isBefore(expiresAt);
    }
    
    /**
     * Get QR data string (what gets encoded in the QR image)
     * Format: FOODPOOL:foodDate:token
     */
    public String getQrData() {
        return String.format("FOODPOOL:%s:%s", foodDate, token);
    }
    
    /**
     * Parse QR data string
     * @return array of [foodDate, token] or null if invalid
     */
    public static String[] parseQrData(String qrData) {
        if (qrData == null || !qrData.startsWith("FOODPOOL:")) {
            return null;
        }
        String[] parts = qrData.split(":");
        if (parts.length != 3) {
            return null;
        }
        return new String[] { parts[1], parts[2] };
    }
}

