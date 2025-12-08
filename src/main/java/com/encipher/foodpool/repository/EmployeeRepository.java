package com.encipher.foodpool.repository;

import com.encipher.foodpool.model.Employee;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends MongoRepository<Employee, String> {
    Optional<Employee> findByEmployeeId(String employeeId);
    Optional<Employee> findByEmail(String email);
    Optional<Employee> findByEmailIgnoreCase(String email);
    Optional<Employee> findByNameIgnoreCase(String name);
    Optional<Employee> findByNameContainingIgnoreCase(String name);
    List<Employee> findByIsAdminTrue();
    List<Employee> findByIsActiveTrue();
    List<Employee> findByIsActiveTrueOrderByNameAsc();
}
