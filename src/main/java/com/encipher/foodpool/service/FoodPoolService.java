package com.encipher.foodpool.service;

import com.encipher.foodpool.model.*;
import com.encipher.foodpool.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FoodPoolService {
    
    private final FoodPoolRepository foodPoolRepository;
    private final FoodScanRepository foodScanRepository;
    private final MenuConfigRepository menuConfigRepository;
    private final CliqNotificationService cliqNotificationService;
    
    // ============== MENU CONFIG ==============
    
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
     * Find the survey config where food date is the given date
     * This tells us which survey created food for this date
     */
    public Optional<MenuConfig> getSurveyForFoodDate(LocalDate foodDate) {
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
     * Check if today is a food collection day (there's a survey with foodDate = today)
     */
    public boolean isFoodCollectionDay() {
        LocalDate today = LocalDate.now();
        return getSurveyForFoodDate(today).isPresent();
    }
    
    /**
     * Get the food date from today's active survey (if any)
     */
    public LocalDate getFoodDateFromTodaySurvey() {
        MenuConfig menu = getTodayMenu();
        return menu.getFoodDate() != null ? menu.getFoodDate() : LocalDate.now().plusDays(1);
    }
    
    // ============== POOL OPERATIONS ==============
    
    /**
     * Admin: Start the pool/survey with duration and food date
     */
    public MenuConfig openPool(String adminEmail, int durationHours, LocalDate foodDate) {
        MenuConfig config = getTodayMenu();
        
        if (!config.isFoodAvailable()) {
            throw new RuntimeException("Food is not available today. Configure menu first.");
        }
        
        if (durationHours < 1) durationHours = 1;
        if (durationHours > 24) durationHours = 24;
        
        LocalDateTime now = LocalDateTime.now();
        
        config.setPoolOpen(true);
        config.setPoolOpenedAt(now);
        config.setPoolOpenedBy(adminEmail);
        config.setPoolDurationHours(durationHours);
        config.setPoolAutoCloseAt(now.plusHours(durationHours));
        config.setPoolClosedAt(null);
        config.setFoodDate(foodDate);
        
        config.setNotificationPoolStarted(true);
        config.setNotification1HourBefore(false);
        config.setNotification50Percent(false);
        config.setNotification70Percent(false);
        
        MenuConfig saved = menuConfigRepository.save(config);
        
        try {
            cliqNotificationService.notifyPoolStarted(durationHours, foodDate);
        } catch (Exception e) {
            log.error("Failed to send pool started notification: {}", e.getMessage());
        }
        
        return saved;
    }
    
    public MenuConfig openPool(String adminEmail, int durationHours) {
        return openPool(adminEmail, durationHours, LocalDate.now().plusDays(1));
    }
    
    public MenuConfig openPool(String adminEmail) {
        return openPool(adminEmail, 6, LocalDate.now().plusDays(1));
    }
    
    /**
     * Admin: Close the pool
     */
    public MenuConfig closePool(String adminEmail) {
        MenuConfig config = getTodayMenu();
        config.setPoolOpen(false);
        config.setPoolClosedAt(LocalDateTime.now());
        config.setUpdatedBy(adminEmail);
        
        MenuConfig saved = menuConfigRepository.save(config);
        
        try {
            Map<String, Long> stats = getStatsForFoodDate(config.getFoodDate());
            cliqNotificationService.notifyPoolClosed(stats.get("veg"), stats.get("nonveg"));
        } catch (Exception e) {
            log.error("Failed to send pool closed notification: {}", e.getMessage());
        }
        
        return saved;
    }
    
    /**
     * Admin: Configure today's menu
     */
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
        
        if (!foodAvailable) {
            config.setPoolOpen(false);
        }
        
        return menuConfigRepository.save(config);
    }
    
    // ============== SCHEDULED TASKS ==============
    
    @Scheduled(fixedRate = 60000)
    public void checkPoolAutoClose() {
        LocalDate today = LocalDate.now();
        menuConfigRepository.findByDate(today).ifPresent(config -> {
            if (config.isPoolOpen() && config.getPoolAutoCloseAt() != null) {
                LocalDateTime now = LocalDateTime.now();
                
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
    
    @Scheduled(fixedRate = 30000)
    public void checkFoodConsumption() {
        LocalDate today = LocalDate.now();
        // Check if today is a food collection day
        getSurveyForFoodDate(today).ifPresent(config -> {
            Map<String, Long> stats = getStatsForFoodDate(today);
            long total = stats.get("total");
            long collected = stats.get("collected");
            
            if (total > 0) {
                double percentage = (collected * 100.0) / total;
                
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
    
    public boolean hasPooledForFoodDate(String employeeId, LocalDate foodDate) {
        return getPoolForFoodDate(employeeId, foodDate).isPresent();
    }
    
    /**
     * Get pool for the current active survey's food date
     */
    public Optional<FoodPool> getPoolForCurrentSurvey(String employeeId) {
        LocalDate foodDate = getFoodDateFromTodaySurvey();
        return getPoolForFoodDate(employeeId, foodDate);
    }
    
    public boolean hasPooledForCurrentSurvey(String employeeId) {
        return getPoolForCurrentSurvey(employeeId).isPresent();
    }
    
    /**
     * Check if user can change their food preference (pool must be open)
     */
    public boolean canChangePoolChoice(String employeeId) {
        return isPoolOpen();
    }
    
    /**
     * Register or UPDATE food pool preference
     * User can change preference while pool is open
     */
    public FoodPool registerPool(Employee employee, String foodType) throws Exception {
        MenuConfig menu = getTodayMenu();
        
        if (!menu.isFoodAvailable()) {
            throw new Exception("Food survey is not available today");
        }
        
        if (!menu.isPoolOpen()) {
            throw new Exception("Pool is closed. You cannot register or change your preference.");
        }
        
        LocalDate foodDate = menu.getFoodDate() != null ? menu.getFoodDate() : LocalDate.now().plusDays(1);
        
        // Check menu availability
        if ("veg".equals(foodType) && !menu.isVegAvailable()) {
            throw new Exception("Veg option not available for this survey");
        }
        if ("nonveg".equals(foodType) && !menu.isNonvegAvailable()) {
            throw new Exception("Non-veg option not available for this survey");
        }
        
        // Check if already pooled for this food date - UPDATE if exists
        Optional<FoodPool> existingPool = getPoolForFoodDate(employee.getEmployeeId(), foodDate);
        if (existingPool.isPresent()) {
            FoodPool pool = existingPool.get();
            String oldType = pool.getFoodType();
            pool.setFoodType(foodType);
            pool.setTimestamp(LocalDateTime.now());
            log.info("Employee {} changed preference from {} to {} for food date {}", 
                    employee.getName(), oldType, foodType, foodDate);
            return foodPoolRepository.save(pool);
        }
        
        // Create new pool entry
        FoodPool pool = new FoodPool(
                employee.getEmployeeId(),
                employee.getName(),
                employee.getEmail(),
                foodType,
                LocalDate.now()  // Survey date
        );
        pool.setFoodDate(foodDate);  // When food will be collected
        
        log.info("Employee {} registered for {} on food date {}", employee.getName(), foodType, foodDate);
        return foodPoolRepository.save(pool);
    }
    
    /**
     * Get all pools for a specific food date
     */
    public List<FoodPool> getPoolsForFoodDate(LocalDate foodDate) {
        return foodPoolRepository.findByFoodDateOrderByTimestampDesc(foodDate);
    }
    
    /**
     * Get all pools for the current survey's food date
     */
    public List<FoodPool> getCurrentSurveyPools() {
        LocalDate foodDate = getFoodDateFromTodaySurvey();
        return getPoolsForFoodDate(foodDate);
    }
    
    /**
     * Get stats for a specific food date
     */
    public Map<String, Long> getStatsForFoodDate(LocalDate foodDate) {
        if (foodDate == null) foodDate = LocalDate.now();
        
        long vegCount = foodPoolRepository.countByFoodDateAndFoodType(foodDate, "veg");
        long nonvegCount = foodPoolRepository.countByFoodDateAndFoodType(foodDate, "nonveg");
        long collectedCount = foodScanRepository.countByFoodDate(foodDate);
        long collectedWithVote = foodScanRepository.countByFoodDateAndDidVote(foodDate, true);
        long collectedWithoutVote = foodScanRepository.countByFoodDateAndDidVote(foodDate, false);
        
        Map<String, Long> stats = new HashMap<>();
        stats.put("veg", vegCount);
        stats.put("nonveg", nonvegCount);
        stats.put("total", vegCount + nonvegCount);
        stats.put("collected", collectedCount);
        stats.put("collectedWithVote", collectedWithVote);
        stats.put("collectedWithoutVote", collectedWithoutVote);
        
        return stats;
    }
    
    /**
     * Get stats for today's collection (if today is a food day)
     */
    public Map<String, Long> getTodayCollectionStats() {
        return getStatsForFoodDate(LocalDate.now());
    }
    
    /**
     * Get stats for the current survey's food date
     */
    public Map<String, Long> getCurrentSurveyStats() {
        LocalDate foodDate = getFoodDateFromTodaySurvey();
        return getStatsForFoodDate(foodDate);
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
    
    public boolean hasCollectedToday(String employeeId) {
        return hasCollectedForFoodDate(employeeId, LocalDate.now());
    }
    
    /**
     * Record food collection - ANYONE can collect food on collection day
     * Track if they voted or not in metrics
     */
    public FoodScan recordScan(Employee employee, String qrData) throws Exception {
        LocalDate today = LocalDate.now();
        
        // Check if today is a food collection day
        if (!isFoodCollectionDay()) {
            throw new Exception("Today is not a food collection day. No food is scheduled for today.");
        }
        
        // Check if already collected
        if (hasCollectedToday(employee.getEmployeeId())) {
            throw new Exception("You have already collected food today");
        }
        
        // Check if employee voted for today's food
        Optional<FoodPool> poolOpt = getPoolForFoodDate(employee.getEmployeeId(), today);
        boolean didVote = poolOpt.isPresent();
        String foodType = poolOpt.map(FoodPool::getFoodType).orElse("unknown");
        
        FoodScan scan = new FoodScan(
                employee.getEmployeeId(),
                employee.getName(),
                foodType,
                today,
                qrData
        );
        scan.setFoodDate(today);
        scan.setDidVote(didVote);
        
        log.info("Employee {} collected food. Voted: {}, FoodType: {}", 
                employee.getName(), didVote, foodType);
        
        return foodScanRepository.save(scan);
    }
    
    /**
     * Get all scans for a specific food date
     */
    public List<FoodScan> getScansForFoodDate(LocalDate foodDate) {
        return foodScanRepository.findByFoodDateOrderByScanTimeDesc(foodDate);
    }
    
    /**
     * Get today's scans
     */
    public List<FoodScan> getTodayScans() {
        return getScansForFoodDate(LocalDate.now());
    }
    
    // ============== BATCH QUERIES FOR REPORTS ==============
    
    /**
     * Get all pools in a date range (by food date) - BATCH QUERY
     */
    public List<FoodPool> getPoolsForDateRange(LocalDate startDate, LocalDate endDate) {
        return foodPoolRepository.findByFoodDateBetweenOrderByFoodDateAscTimestampDesc(startDate, endDate);
    }
    
    /**
     * Get all scans in a date range (by food date) - BATCH QUERY
     */
    public List<FoodScan> getScansForDateRange(LocalDate startDate, LocalDate endDate) {
        return foodScanRepository.findByFoodDateBetweenOrderByFoodDateAscScanTimeDesc(startDate, endDate);
    }
    
    /**
     * Get unique food dates with activity in a date range
     */
    public Set<LocalDate> getFoodDatesWithActivity(LocalDate startDate, LocalDate endDate) {
        Set<LocalDate> dates = new HashSet<>();
        
        List<FoodPool> pools = getPoolsForDateRange(startDate, endDate);
        for (FoodPool pool : pools) {
            if (pool.getFoodDate() != null) {
                dates.add(pool.getFoodDate());
            }
        }
        
        List<FoodScan> scans = getScansForDateRange(startDate, endDate);
        for (FoodScan scan : scans) {
            if (scan.getFoodDate() != null) {
                dates.add(scan.getFoodDate());
            }
        }
        
        return dates;
    }
}
