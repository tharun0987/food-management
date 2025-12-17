package com.encipher.foodpool.repository;

import com.encipher.foodpool.model.FoodScan;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FoodScanRepository extends MongoRepository<FoodScan, String> {
    // By scan date
    Optional<FoodScan> findByEmployeeIdAndDate(String employeeId, LocalDate date);
    List<FoodScan> findByDate(LocalDate date);
    long countByDate(LocalDate date);
    List<FoodScan> findByDateOrderByScanTimeDesc(LocalDate date);
    
    // By food date
    Optional<FoodScan> findByEmployeeIdAndFoodDate(String employeeId, LocalDate foodDate);
    List<FoodScan> findByFoodDate(LocalDate foodDate);
    long countByFoodDate(LocalDate foodDate);
    List<FoodScan> findByFoodDateOrderByScanTimeDesc(LocalDate foodDate);
    
    // Track vote status
    long countByFoodDateAndDidVote(LocalDate foodDate, boolean didVote);
    List<FoodScan> findByFoodDateAndDidVote(LocalDate foodDate, boolean didVote);
}
