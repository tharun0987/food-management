package com.encipher.foodpool.repository;

import com.encipher.foodpool.model.GraceRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface GraceRequestRepository extends MongoRepository<GraceRequest, String> {
    
    // Find by employee and food date
    Optional<GraceRequest> findByEmployeeIdAndFoodDate(String employeeId, LocalDate foodDate);
    
    // Find all pending requests
    List<GraceRequest> findByStatusOrderByRequestedAtDesc(String status);
    
    // Find pending requests for a specific food date
    List<GraceRequest> findByStatusAndFoodDateOrderByRequestedAtDesc(String status, LocalDate foodDate);
    
    // Find all requests for a food date
    List<GraceRequest> findByFoodDateOrderByRequestedAtDesc(LocalDate foodDate);
    
    // Find requests by employee
    List<GraceRequest> findByEmployeeIdOrderByRequestedAtDesc(String employeeId);
    
    // Find requests by reviewer
    List<GraceRequest> findByReviewedByOrderByReviewedAtDesc(String reviewedBy);
    
    // Count pending requests
    long countByStatus(String status);
    
    // Count pending requests for a food date
    long countByStatusAndFoodDate(String status, LocalDate foodDate);
}

