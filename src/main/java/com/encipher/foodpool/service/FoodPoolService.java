package com.encipher.foodpool.service;

import com.encipher.foodpool.model.*;
import com.encipher.foodpool.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final CliqNotificationService cliqNotificationService;
    
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
    
    /**
     * Find menu config where food date is today (for scanning)
     */
    public Optional<MenuConfig> getMenuForFoodDate(LocalDate foodDate) {
        return menuConfigRepository.findByFoodDate(foodDate);
    }
    
    public boolean isPoolOpen() {
        MenuConfig menu = getTodayMenu();
        return menu.isFoodAvailable() && menu.isPoolOpen();
    }
    
    public boolean isFoodAvailableToday() {
        return getTodayMenu().isFoodAvailable();
    }
    
    /**
     * Check if today is a food collection day
     */
    public boolean isFoodCollectionDay() {
        LocalDate today = LocalDate.now();
        Optional<MenuConfig> config = menuConfigRepository.findByFoodDate(today);
        return config.isPresent();
    }
    
    /**
     * Get the food date from today's survey
     */
    public LocalDate getFoodDateFromTodaySurvey() {
        MenuConfig menu = getTodayMenu();
        return menu.getFoodDate() != null ? menu.getFoodDate() : LocalDate.now().plusDays(1);
    }
    
    // Admin: Start the pool/survey with duration and food date
    public MenuConfig openPool(String adminEmail, int durationHours, LocalDate foodDate) {
        MenuConfig config = getTodayMenu();
        
        if (!config.isFoodAvailable()) {
            throw new RuntimeException("Food is not available today. Configure menu first.");
        }
        
        // Validate duration (1-24 hours)
        if (durationHours < 1) durationHours = 1;
        if (durationHours > 24) durationHours = 24;
        
        LocalDateTime now = LocalDateTime.now();
        
        config.setPoolOpen(true);
        config.setPoolOpenedAt(now);
        config.setPoolOpenedBy(adminEmail);
        config.setPoolDurationHours(durationHours);
        config.setPoolAutoCloseAt(now.plusHours(durationHours));
        config.setPoolClosedAt(null);
        config.setFoodDate(foodDate);  // Set when food will be served
        
        // Reset notification flags
        config.setNotificationPoolStarted(true);
        config.setNotification1HourBefore(false);
        config.setNotification50Percent(false);
        config.setNotification70Percent(false);
        
        MenuConfig saved = menuConfigRepository.save(config);
        
        // Send Cliq notification
        try {
            cliqNotificationService.notifyPoolStarted(durationHours, foodDate);
        } catch (Exception e) {
            log.error("Failed to send pool started notification: {}", e.getMessage());
        }
        
        return saved;
    }
    
    // Overload for backward compatibility
    public MenuConfig openPool(String adminEmail, int durationHours) {
        return openPool(adminEmail, durationHours, LocalDate.now().plusDays(1));
    }
    
    // Overload for backward compatibility
    public MenuConfig openPool(String adminEmail) {
        return openPool(adminEmail, 6, LocalDate.now().plusDays(1));
    }
    
    // Admin: Close the pool
    public MenuConfig closePool(String adminEmail) {
        MenuConfig config = getTodayMenu();
        config.setPoolOpen(false);
        config.setPoolClosedAt(LocalDateTime.now());
        config.setUpdatedBy(adminEmail);
        
        MenuConfig saved = menuConfigRepository.save(config);
        
        // Send close notification
        try {
            Map<String, Long> stats = getStatsForFoodDate(config.getFoodDate());
            cliqNotificationService.notifyPoolClosed(stats.get("veg"), stats.get("nonveg"));
        } catch (Exception e) {
            log.error("Failed to send pool closed notification: {}", e.getMessage());
        }
        
        return saved;
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
    
    // ============== SCHEDULED TASKS ==============
    
    @Scheduled(fixedRate = 60000)  // Check every minute
    public void checkPoolAutoClose() {
        LocalDate today = LocalDate.now();
        menuConfigRepository.findByDate(today).ifPresent(config -> {
            if (config.isPoolOpen() && config.getPoolAutoCloseAt() != null) {
                LocalDateTime now = LocalDateTime.now();
                
                // Check if should auto-close
                if (now.isAfter(config.getPoolAutoCloseAt())) {
                    log.info("Auto-closing pool for {}", today);
                    config.setPoolOpen(false);
                    config.setPoolClosedAt(now);
                    config.setUpdatedBy("SYSTEM_AUTO_CLOSE");
                    menuConfigRepository.save(config);
                    
                    try {
                        Map<String, Long> stats = getStatsForFoodDate(config.getFoodDate());
                        cliqNotificationService.notifyPoolClosed(stats.get("veg"), stats.get("nonveg"));
                    } catch (Exception e) {
                        log.error("Failed to send auto-close notification: {}", e.getMessage());
                    }
                }
                // Check 1 hour before close notification
                else if (!config.isNotification1HourBefore() && 
                         now.isAfter(config.getPoolAutoCloseAt().minusHours(1))) {
                    log.info("Sending 1 hour before close notification");
                    config.setNotification1HourBefore(true);
                    menuConfigRepository.save(config);
                    
                    try {
                        cliqNotificationService.notifyPoolClosingSoon();
                    } catch (Exception e) {
                        log.error("Failed to send 1 hour notification: {}", e.getMessage());
                    }
                }
            }
        });
    }
    
    @Scheduled(fixedRate = 30000)  // Check every 30 seconds
    public void checkFoodConsumption() {
        LocalDate today = LocalDate.now();
        // Check if today is a food collection day
        menuConfigRepository.findByFoodDate(today).ifPresent(config -> {
            Map<String, Long> stats = getStatsForFoodDate(today);
            long total = stats.get("total");
            long collected = stats.get("collected");
            
            if (total > 0) {
                double percentage = (collected * 100.0) / total;
                
                // 70% notification
                if (!config.isNotification70Percent() && percentage >= 70) {
                    log.info("Sending 70% consumption notification");
                    config.setNotification70Percent(true);
                    menuConfigRepository.save(config);
                    
                    try {
                        cliqNotificationService.notify70PercentConsumed(collected, total);
                    } catch (Exception e) {
                        log.error("Failed to send 70% notification: {}", e.getMessage());
                    }
                }
                // 50% notification
                else if (!config.isNotification50Percent() && percentage >= 50) {
                    log.info("Sending 50% consumption notification");
                    config.setNotification50Percent(true);
                    menuConfigRepository.save(config);
                    
                    try {
                        cliqNotificationService.notify50PercentConsumed(collected, total);
                    } catch (Exception e) {
                        log.error("Failed to send 50% notification: {}", e.getMessage());
                    }
                }
            }
        });
    }
    
    // ============== FOOD POOL (VOTING) ==============
    
    /**
     * Get pool registration for a specific food date
     */
    public Optional<FoodPool> getPoolForFoodDate(String employeeId, LocalDate foodDate) {
        return foodPoolRepository.findByEmployeeIdAndFoodDate(employeeId, foodDate);
    }
    
    /**
     * Check if employee has pooled for today's survey (food date)
     */
    public boolean hasPooledForFoodDate(String employeeId, LocalDate foodDate) {
        return getPoolForFoodDate(employeeId, foodDate).isPresent();
    }
    
    // Legacy method - checks today's date
    public Optional<FoodPool> getPoolForToday(String employeeId) {
        LocalDate foodDate = getFoodDateFromTodaySurvey();
        return getPoolForFoodDate(employeeId, foodDate);
    }
    
    public boolean hasPooledToday(String employeeId) {
        return getPoolForToday(employeeId).isPresent();
    }
    
    public FoodPool registerPool(Employee employee, String foodType) throws Exception {
        MenuConfig menu = getTodayMenu();
        
        // Check if pool is open
        if (!menu.isFoodAvailable()) {
            throw new Exception("Food is not available today");
        }
        
        if (!menu.isPoolOpen()) {
            throw new Exception("Pool is not open yet. Please wait for admin to start the survey.");
        }
        
        LocalDate foodDate = menu.getFoodDate() != null ? menu.getFoodDate() : LocalDate.now().plusDays(1);
        
        // Check if already pooled for this food date
        Optional<FoodPool> existingPool = getPoolForFoodDate(employee.getEmployeeId(), foodDate);
        if (existingPool.isPresent()) {
            throw new Exception("Already registered for " + existingPool.get().getFoodType().toUpperCase() + 
                    ". Your choice is locked until the pool ends.");
        }
        
        // Check menu availability
        if ("veg".equals(foodType) && !menu.isVegAvailable()) {
            throw new Exception("Veg not available");
        }
        if ("nonveg".equals(foodType) && !menu.isNonvegAvailable()) {
            throw new Exception("Non-veg not available");
        }
        
        FoodPool pool = new FoodPool(
                employee.getEmployeeId(),
                employee.getName(),
                employee.getEmail(),
                foodType,
                LocalDate.now()  // Survey date
        );
        pool.setFoodDate(foodDate);  // When food will be collected
        
        return foodPoolRepository.save(pool);
    }
    
    public List<FoodPool> getTodayPools() {
        LocalDate foodDate = getFoodDateFromTodaySurvey();
        return foodPoolRepository.findByFoodDateOrderByTimestampDesc(foodDate);
    }
    
    public List<FoodPool> getPoolsForDate(LocalDate date) {
        return foodPoolRepository.findByDateOrderByTimestampDesc(date);
    }
    
    public List<FoodPool> getPoolsForFoodDate(LocalDate foodDate) {
        return foodPoolRepository.findByFoodDateOrderByTimestampDesc(foodDate);
    }
    
    public Map<String, Long> getTodayStats() {
        LocalDate foodDate = getFoodDateFromTodaySurvey();
        return getStatsForFoodDate(foodDate);
    }
    
    public Map<String, Long> getStatsForFoodDate(LocalDate foodDate) {
        if (foodDate == null) foodDate = LocalDate.now();
        long vegCount = foodPoolRepository.countByFoodDateAndFoodType(foodDate, "veg");
        long nonvegCount = foodPoolRepository.countByFoodDateAndFoodType(foodDate, "nonveg");
        long collectedCount = foodScanRepository.countByFoodDate(foodDate);
        
        return Map.of(
                "veg", vegCount,
                "nonveg", nonvegCount,
                "total", vegCount + nonvegCount,
                "collected", collectedCount
        );
    }
    
    // ============== FOOD SCAN (COLLECTION) ==============
    
    /**
     * Get scan for a specific food date
     */
    public Optional<FoodScan> getScanForFoodDate(String employeeId, LocalDate foodDate) {
        return foodScanRepository.findByEmployeeIdAndFoodDate(employeeId, foodDate);
    }
    
    public boolean hasCollectedForFoodDate(String employeeId, LocalDate foodDate) {
        return getScanForFoodDate(employeeId, foodDate).isPresent();
    }
    
    // Legacy method
    public Optional<FoodScan> getScanForToday(String employeeId) {
        return foodScanRepository.findByEmployeeIdAndDate(employeeId, LocalDate.now());
    }
    
    public boolean hasCollectedToday(String employeeId) {
        return getScanForToday(employeeId).isPresent();
    }
    
    /**
     * Record food collection - only allowed on food date
     */
    public FoodScan recordScan(Employee employee, String qrData) throws Exception {
        LocalDate today = LocalDate.now();
        
        // Check if employee has a pool registration for today as food date
        Optional<FoodPool> poolOpt = getPoolForFoodDate(employee.getEmployeeId(), today);
        if (poolOpt.isEmpty()) {
            throw new Exception("You are not registered for food today. Food collection is only available on the scheduled food date.");
        }
        
        // Check if already collected
        if (hasCollectedForFoodDate(employee.getEmployeeId(), today)) {
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
        scan.setFoodDate(today);
        
        return foodScanRepository.save(scan);
    }
    
    public List<FoodScan> getTodayScans() {
        return foodScanRepository.findByDateOrderByScanTimeDesc(LocalDate.now());
    }
    
    public List<FoodScan> getScansForFoodDate(LocalDate foodDate) {
        return foodScanRepository.findByFoodDateOrderByScanTimeDesc(foodDate);
    }
}
