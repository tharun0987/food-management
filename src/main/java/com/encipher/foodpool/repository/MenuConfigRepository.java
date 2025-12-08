package com.encipher.foodpool.repository;

import com.encipher.foodpool.model.MenuConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface MenuConfigRepository extends MongoRepository<MenuConfig, String> {
    Optional<MenuConfig> findByDate(LocalDate date);
}

