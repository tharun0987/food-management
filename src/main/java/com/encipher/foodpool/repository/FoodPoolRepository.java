package com.encipher.foodpool.repository;

import com.encipher.foodpool.model.FoodPool;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FoodPoolRepository extends MongoRepository<FoodPool, String> {
    Optional<FoodPool> findByEmployeeIdAndDate(String employeeId, LocalDate date);
    List<FoodPool> findByDate(LocalDate date);
    List<FoodPool> findByDateOrderByTimestampDesc(LocalDate date);
    long countByDateAndFoodType(LocalDate date, String foodType);
    List<FoodPool> findByDateAndFoodType(LocalDate date, String foodType);
}
