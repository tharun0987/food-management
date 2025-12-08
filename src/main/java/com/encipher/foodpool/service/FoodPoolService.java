package com.encipher.foodpool.service;

import com.encipher.foodpool.model.*;
import com.encipher.foodpool.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FoodPoolService {
    
    private final FoodPoolRepository foodPoolRepository;
    private final FoodScanRepository foodScanRepository;
    private final MenuConfigRepository menuConfigRepository;
    
    // ============== MENU ==============
    
    public MenuConfig getTodayMenu() {
        LocalDate today = LocalDate.now();
        return menuConfigRepository.findByDate(today)
                .orElseGet(() -> {
                    MenuConfig config = new MenuConfig(today);
                    return menuConfigRepository.save(config);
                });
    }
    
    public MenuConfig getMenuForDate(LocalDate date) {
        return menuConfigRepository.findByDate(date)
                .orElseGet(() -> new MenuConfig(date));
    }
    
    public boolean isPoolOpen() {
        MenuConfig menu = getTodayMenu();
        return menu.isFoodAvailable() && menu.isPoolOpen();
    }
    
    public boolean isFoodAvailableToday() {
        return getTodayMenu().isFoodAvailable();
    }
    
    // Admin: Start the pool/survey
    public MenuConfig openPool(String adminEmail) {
        MenuConfig config = getTodayMenu();
        
        if (!config.isFoodAvailable()) {
            throw new RuntimeException("Food is not available today. Configure menu first.");
        }
        
        config.setPoolOpen(true);
        config.setPoolOpenedAt(LocalDateTime.now());
        config.setPoolOpenedBy(adminEmail);
        
        return menuConfigRepository.save(config);
    }
    
    // Admin: Close the pool
    public MenuConfig closePool(String adminEmail) {
        MenuConfig config = getTodayMenu();
        config.setPoolOpen(false);
        config.setPoolClosedAt(LocalDateTime.now());
        config.setUpdatedBy(adminEmail);
        return menuConfigRepository.save(config);
    }
    
    // Admin: Configure today's menu
    public MenuConfig updateMenu(LocalDate date, boolean foodAvailable, 
                                  boolean vegAvailable, boolean nonvegAvailable,
                                  List<String> vegItems, List<String> nonvegItems, 
                                  String updatedBy) {
        MenuConfig config = menuConfigRepository.findByDate(date)
                .orElseGet(() -> new MenuConfig(date));
        
        config.setFoodAvailable(foodAvailable);
        config.setVegAvailable(vegAvailable);
        config.setNonvegAvailable(nonvegAvailable);
        config.setVegItems(vegItems != null ? vegItems : List.of());
        config.setNonvegItems(nonvegItems != null ? nonvegItems : List.of());
        config.setUpdatedAt(LocalDateTime.now());
        config.setUpdatedBy(updatedBy);
        
        // If food not available, close pool
        if (!foodAvailable) {
            config.setPoolOpen(false);
        }
        
        return menuConfigRepository.save(config);
    }
    
    // ============== FOOD POOL ==============
    
    public Optional<FoodPool> getPoolForToday(String employeeId) {
        return foodPoolRepository.findByEmployeeIdAndDate(employeeId, LocalDate.now());
    }
    
    public boolean hasPooledToday(String employeeId) {
        return getPoolForToday(employeeId).isPresent();
    }
    
    public FoodPool registerPool(Employee employee, String foodType) throws Exception {
        LocalDate today = LocalDate.now();
        MenuConfig menu = getTodayMenu();
        
        // Check if pool is open
        if (!menu.isFoodAvailable()) {
            throw new Exception("Food is not available today");
        }
        
        if (!menu.isPoolOpen()) {
            throw new Exception("Pool is not open yet. Please wait for admin to start the survey.");
        }
        
        // Check if already pooled
        if (hasPooledToday(employee.getEmployeeId())) {
            throw new Exception("Already registered for today");
        }
        
        // Check menu availability
        if ("veg".equals(foodType) && !menu.isVegAvailable()) {
            throw new Exception("Veg not available today");
        }
        if ("nonveg".equals(foodType) && !menu.isNonvegAvailable()) {
            throw new Exception("Non-veg not available today");
        }
        
        FoodPool pool = new FoodPool(
                employee.getEmployeeId(),
                employee.getName(),
                employee.getEmail(),
                foodType,
                today
        );
        
        return foodPoolRepository.save(pool);
    }
    
    public List<FoodPool> getTodayPools() {
        return foodPoolRepository.findByDateOrderByTimestampDesc(LocalDate.now());
    }
    
    public List<FoodPool> getPoolsForDate(LocalDate date) {
        return foodPoolRepository.findByDateOrderByTimestampDesc(date);
    }
    
    public Map<String, Long> getTodayStats() {
        LocalDate today = LocalDate.now();
        long vegCount = foodPoolRepository.countByDateAndFoodType(today, "veg");
        long nonvegCount = foodPoolRepository.countByDateAndFoodType(today, "nonveg");
        long collectedCount = foodScanRepository.countByDate(today);
        
        return Map.of(
                "veg", vegCount,
                "nonveg", nonvegCount,
                "total", vegCount + nonvegCount,
                "collected", collectedCount
        );
    }
    
    // ============== FOOD SCAN ==============
    
    public Optional<FoodScan> getScanForToday(String employeeId) {
        return foodScanRepository.findByEmployeeIdAndDate(employeeId, LocalDate.now());
    }
    
    public boolean hasCollectedToday(String employeeId) {
        return getScanForToday(employeeId).isPresent();
    }
    
    public FoodScan recordScan(Employee employee, String qrData) throws Exception {
        LocalDate today = LocalDate.now();
        
        // Check if pooled
        Optional<FoodPool> poolOpt = getPoolForToday(employee.getEmployeeId());
        if (poolOpt.isEmpty()) {
            throw new Exception("Not registered for food pool today");
        }
        
        // Check if already collected
        if (hasCollectedToday(employee.getEmployeeId())) {
            throw new Exception("Food already collected today");
        }
        
        FoodPool pool = poolOpt.get();
        FoodScan scan = new FoodScan(
                employee.getEmployeeId(),
                employee.getName(),
                pool.getFoodType(),
                today,
                qrData
        );
        
        return foodScanRepository.save(scan);
    }
    
    public List<FoodScan> getTodayScans() {
        return foodScanRepository.findByDateOrderByScanTimeDesc(LocalDate.now());
    }
}
