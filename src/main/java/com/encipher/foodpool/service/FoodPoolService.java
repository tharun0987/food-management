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
    
    public boolean isPoolOpen() {
        MenuConfig menu = getTodayMenu();
        return menu.isFoodAvailable() && menu.isPoolOpen();
    }
    
    public boolean isFoodAvailableToday() {
        return getTodayMenu().isFoodAvailable();
    }
    
    // Admin: Start the pool/survey with duration
    public MenuConfig openPool(String adminEmail, int durationHours) {
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
        
        // Reset notification flags
        config.setNotificationPoolStarted(true);
        config.setNotification1HourBefore(false);
        config.setNotification50Percent(false);
        config.setNotification70Percent(false);
        
        MenuConfig saved = menuConfigRepository.save(config);
        
        // Send Cliq notification
        try {
            cliqNotificationService.notifyPoolStarted(durationHours);
        } catch (Exception e) {
            log.error("Failed to send pool started notification: {}", e.getMessage());
        }
        
        return saved;
    }
    
    // Overload for backward compatibility
    public MenuConfig openPool(String adminEmail) {
        return openPool(adminEmail, 6);  // Default 6 hours
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
            Map<String, Long> stats = getTodayStats();
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
                        Map<String, Long> stats = getTodayStats();
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
        menuConfigRepository.findByDate(today).ifPresent(config -> {
            if (config.isPoolOpen()) {
                Map<String, Long> stats = getTodayStats();
                long total = stats.get("total");
                long collected = stats.get("collected");
                
                if (total > 0) {
                    double percentage = (collected * 100.0) / total;
                    
                    // 70% notification (check first since it's higher)
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
            }
        });
    }
    
    // ============== FOOD POOL ==============
    
    public Optional<FoodPool> getPoolForToday(String employeeId) {
        return foodPoolRepository.findByEmployeeIdAndDate(employeeId, LocalDate.now());
    }
    
    public boolean hasPooledToday(String employeeId) {
        return getPoolForToday(employeeId).isPresent();
    }
    
    /**
     * Check if user can change their food choice
     * Once pooled, cannot change until pool is closed
     */
    public boolean canChangePoolChoice(String employeeId) {
        // If not pooled, they can make a choice
        if (!hasPooledToday(employeeId)) {
            return true;
        }
        
        // If pooled, they cannot change while pool is open
        MenuConfig menu = getTodayMenu();
        return !menu.isPoolOpen();  // Can only change after pool closes (which means they can't)
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
        
        // Check if already pooled - LOCKED until pool stops
        Optional<FoodPool> existingPool = getPoolForToday(employee.getEmployeeId());
        if (existingPool.isPresent()) {
            throw new Exception("Already registered for " + existingPool.get().getFoodType().toUpperCase() + 
                    ". Your choice is locked until the pool ends.");
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
