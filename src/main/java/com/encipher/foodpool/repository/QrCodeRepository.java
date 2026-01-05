package com.encipher.foodpool.repository;

import com.encipher.foodpool.model.QrCode;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface QrCodeRepository extends MongoRepository<QrCode, String> {
    
    // Find by token
    Optional<QrCode> findByToken(String token);
    
    // Find active QR codes for a food date
    List<QrCode> findByFoodDateAndIsActiveTrueOrderByGeneratedAtDesc(LocalDate foodDate);
    
    // Find all QR codes for a food date
    List<QrCode> findByFoodDateOrderByGeneratedAtDesc(LocalDate foodDate);
    
    // Find active QR codes
    List<QrCode> findByIsActiveTrueOrderByGeneratedAtDesc();
    
    // Find expired QR codes (for cleanup)
    List<QrCode> findByExpiresAtBeforeAndIsActiveTrue(LocalDateTime now);
    
    // Find by generator
    List<QrCode> findByGeneratedByOrderByGeneratedAtDesc(String email);
    
    // Find valid QR codes (active and not expired)
    List<QrCode> findByIsActiveTrueAndExpiresAtAfterOrderByGeneratedAtDesc(LocalDateTime now);
    
    // Count active QR codes for a food date
    long countByFoodDateAndIsActiveTrue(LocalDate foodDate);
}

