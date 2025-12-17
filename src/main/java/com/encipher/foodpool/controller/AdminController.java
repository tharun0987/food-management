package com.encipher.foodpool.controller;

import com.encipher.foodpool.model.*;
import com.encipher.foodpool.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {
    
    private final EmployeeService employeeService;
    private final FoodPoolService foodPoolService;
    private final CliqNotificationService cliqNotificationService;
    
    private void addCommonAttributes(OAuth2User user, Model model) {
        model.addAttribute("employeeName", user.getAttribute("employeeName"));
        model.addAttribute("email", user.getAttribute("Email"));
        
        Boolean isAdministrator = user.getAttribute("isAdministrator");
        Boolean isContributor = user.getAttribute("isContributor");
        String role = user.getAttribute("role");
        
        model.addAttribute("isAdministrator", isAdministrator != null && isAdministrator);
        model.addAttribute("isContributor", isContributor != null && isContributor);
        model.addAttribute("userRole", role != null ? role : "USER");
    }
    
    @GetMapping("")
    public String dashboard(
            @AuthenticationPrincipal OAuth2User user, 
            @RequestParam(required = false) String viewDate,
            Model model) {
        addCommonAttributes(user, model);
        
        LocalDate today = LocalDate.now();
        
        // Today's menu and survey status
        MenuConfig menu = foodPoolService.getTodayMenu();
        model.addAttribute("menu", menu);
        model.addAttribute("poolOpen", menu.isPoolOpen());
        model.addAttribute("foodAvailable", menu.isFoodAvailable());
        
        // Current survey food date (the date food will be served from today's survey)
        LocalDate surveyFoodDate = menu.getFoodDate() != null ? menu.getFoodDate() : today.plusDays(1);
        model.addAttribute("surveyFoodDate", surveyFoodDate);
        model.addAttribute("surveyFoodDateFormatted", surveyFoodDate.format(DateTimeFormatter.ofPattern("EEEE, MMM dd")));
        
        // Pool time info
        if (menu.isPoolOpen() && menu.getPoolAutoCloseAt() != null) {
            long minutesLeft = ChronoUnit.MINUTES.between(LocalDateTime.now(), menu.getPoolAutoCloseAt());
            model.addAttribute("minutesLeft", Math.max(0, minutesLeft));
            model.addAttribute("autoCloseTime", menu.getPoolAutoCloseAt().format(DateTimeFormatter.ofPattern("hh:mm a")));
        }
        
        // Check if today is a food COLLECTION day
        boolean isFoodCollectionDay = foodPoolService.isFoodCollectionDay();
        model.addAttribute("isFoodCollectionDay", isFoodCollectionDay);
        
        // ========== COLLECTION STATS (for today if it's a food day) ==========
        if (isFoodCollectionDay) {
            Map<String, Long> collectionStats = foodPoolService.getTodayCollectionStats();
            model.addAttribute("collectionVegCount", collectionStats.get("veg"));
            model.addAttribute("collectionNonvegCount", collectionStats.get("nonveg"));
            model.addAttribute("collectionTotalVoted", collectionStats.get("total"));
            model.addAttribute("collectionCollected", collectionStats.get("collected"));
            model.addAttribute("collectionWithVote", collectionStats.getOrDefault("collectedWithVote", 0L));
            model.addAttribute("collectionWithoutVote", collectionStats.getOrDefault("collectedWithoutVote", 0L));
            
            long collTotal = collectionStats.get("total");
            long collCollected = collectionStats.get("collected");
            model.addAttribute("collectionPercent", collTotal > 0 ? (collCollected * 100 / collTotal) : 0);
            
            model.addAttribute("collectionPools", foodPoolService.getPoolsForFoodDate(today));
            model.addAttribute("collectionScans", foodPoolService.getTodayScans());
        }
        
        // ========== SURVEY STATS (for current survey's food date) ==========
        Map<String, Long> surveyStats = foodPoolService.getStatsForFoodDate(surveyFoodDate);
        model.addAttribute("surveyVegCount", surveyStats.get("veg"));
        model.addAttribute("surveyNonvegCount", surveyStats.get("nonveg"));
        model.addAttribute("surveyTotalVoted", surveyStats.get("total"));
        model.addAttribute("surveyPools", foodPoolService.getPoolsForFoodDate(surveyFoodDate));
        
        // Total employees for participation calculation
        long totalEmployees = employeeService.getAllActiveEmployees().size();
        model.addAttribute("totalEmployees", totalEmployees);
        
        long surveyTotal = surveyStats.get("total");
        model.addAttribute("surveyNotVoted", Math.max(0, totalEmployees - surveyTotal));
        model.addAttribute("surveyParticipationPercent", totalEmployees > 0 ? (surveyTotal * 100 / totalEmployees) : 0);
        
        // ========== HISTORICAL VIEW (for date navigation) ==========
        LocalDate viewingDate = today;
        if (viewDate != null && !viewDate.isEmpty()) {
            try {
                viewingDate = LocalDate.parse(viewDate);
            } catch (Exception e) {
                viewingDate = today;
            }
        }
        
        model.addAttribute("viewDate", viewingDate);
        model.addAttribute("viewDateFormatted", viewingDate.format(DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy")));
        
        // Historical stats for the selected date
        Map<String, Long> historyStats = foodPoolService.getStatsForFoodDate(viewingDate);
        model.addAttribute("historyVegCount", historyStats.get("veg"));
        model.addAttribute("historyNonvegCount", historyStats.get("nonveg"));
        model.addAttribute("historyTotalVoted", historyStats.get("total"));
        model.addAttribute("historyCollected", historyStats.get("collected"));
        model.addAttribute("historyWithVote", historyStats.getOrDefault("collectedWithVote", 0L));
        model.addAttribute("historyWithoutVote", historyStats.getOrDefault("collectedWithoutVote", 0L));
        model.addAttribute("historyPools", foodPoolService.getPoolsForFoodDate(viewingDate));
        model.addAttribute("historyScans", foodPoolService.getScansForFoodDate(viewingDate));
        
        // Date navigation
        model.addAttribute("today", today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        model.addAttribute("todayDisplay", today.format(DateTimeFormatter.ofPattern("EEEE, MMM dd")));
        model.addAttribute("prevDay", viewingDate.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        model.addAttribute("nextDay", viewingDate.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        model.addAttribute("prevWeek", viewingDate.minusWeeks(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        model.addAttribute("nextWeek", viewingDate.plusWeeks(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        
        return "admin/dashboard";
    }
    
    @GetMapping("/stats/{date}")
    @ResponseBody
    public ResponseEntity<?> getStatsForDate(@PathVariable String date) {
        try {
            LocalDate localDate = LocalDate.parse(date);
            Map<String, Long> stats = foodPoolService.getStatsForFoodDate(localDate);
            List<FoodPool> pools = foodPoolService.getPoolsForFoodDate(localDate);
            List<FoodScan> scans = foodPoolService.getScansForFoodDate(localDate);
            
            long totalEmployees = employeeService.getAllActiveEmployees().size();
            
            return ResponseEntity.ok(Map.of(
                "stats", stats,
                "pools", pools,
                "scans", scans,
                "totalEmployees", totalEmployees,
                "date", localDate.format(DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy"))
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/pool/open")
    @ResponseBody
    public ResponseEntity<?> openPool(
            @AuthenticationPrincipal OAuth2User user,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            String email = user.getAttribute("Email");
            
            int duration = 6;
            if (body != null && body.containsKey("duration")) {
                Object durationObj = body.get("duration");
                if (durationObj instanceof Integer) {
                    duration = (Integer) durationObj;
                } else if (durationObj instanceof String) {
                    duration = Integer.parseInt((String) durationObj);
                }
            }
            
            LocalDate foodDate = LocalDate.now().plusDays(1);
            if (body != null && body.containsKey("foodDate")) {
                String foodDateStr = (String) body.get("foodDate");
                if (foodDateStr != null && !foodDateStr.isEmpty()) {
                    foodDate = LocalDate.parse(foodDateStr);
                }
            }
            
            if (duration < 1) duration = 1;
            if (duration > 24) duration = 24;
            
            foodPoolService.openPool(email, duration, foodDate);
            return ResponseEntity.ok(Map.of(
                    "success", true, 
                    "message", "Survey started for " + duration + " hours. Food date: " + 
                              foodDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/pool/close")
    @ResponseBody
    public ResponseEntity<?> closePool(@AuthenticationPrincipal OAuth2User user) {
        try {
            String email = user.getAttribute("Email");
            foodPoolService.closePool(email);
            return ResponseEntity.ok(Map.of("success", true, "message", "Survey closed successfully."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    // ============== NOTIFICATION ENDPOINTS ==============
    
    @PostMapping("/notify/participate")
    @ResponseBody
    public ResponseEntity<?> sendParticipateReminder(@AuthenticationPrincipal OAuth2User user) {
        try {
            MenuConfig menu = foodPoolService.getTodayMenu();
            LocalDate foodDate = menu.getFoodDate() != null ? menu.getFoodDate() : LocalDate.now().plusDays(1);
            cliqNotificationService.sendParticipateReminder(foodDate);
            return ResponseEntity.ok(Map.of("success", true, "message", "Reminder sent to participate in survey"));
        } catch (Exception e) {
            log.error("Failed to send participate reminder: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to send notification: " + e.getMessage()));
        }
    }
    
    @PostMapping("/notify/eat")
    @ResponseBody
    public ResponseEntity<?> sendEatReminder(@AuthenticationPrincipal OAuth2User user) {
        try {
            Map<String, Long> stats = foodPoolService.getTodayCollectionStats();
            long remaining = stats.get("total") - stats.get("collected");
            cliqNotificationService.sendEatReminder(remaining);
            return ResponseEntity.ok(Map.of("success", true, "message", "Reminder sent to collect food"));
        } catch (Exception e) {
            log.error("Failed to send eat reminder: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to send notification: " + e.getMessage()));
        }
    }
    
    @PostMapping("/notify/lastcall")
    @ResponseBody
    public ResponseEntity<?> sendLastCallReminder(@AuthenticationPrincipal OAuth2User user) {
        try {
            cliqNotificationService.sendLastCallReminder();
            return ResponseEntity.ok(Map.of("success", true, "message", "Last call reminder sent"));
        } catch (Exception e) {
            log.error("Failed to send last call reminder: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to send notification: " + e.getMessage()));
        }
    }
    
    @PostMapping("/notify/custom")
    @ResponseBody
    public ResponseEntity<?> sendCustomNotification(
            @AuthenticationPrincipal OAuth2User user,
            @RequestBody Map<String, String> body) {
        try {
            String message = body.get("message");
            if (message == null || message.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Message cannot be empty"));
            }
            cliqNotificationService.sendCustomNotification(message);
            return ResponseEntity.ok(Map.of("success", true, "message", "Custom notification sent"));
        } catch (Exception e) {
            log.error("Failed to send custom notification: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to send notification: " + e.getMessage()));
        }
    }
    
    // ============== MENU ==============
    
    @GetMapping("/menu")
    public String menuPage(@AuthenticationPrincipal OAuth2User user, Model model) {
        addCommonAttributes(user, model);
        model.addAttribute("menu", foodPoolService.getTodayMenu());
        model.addAttribute("today", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        model.addAttribute("todayDisplay", LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM dd")));
        model.addAttribute("tomorrow", LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        model.addAttribute("tomorrowDisplay", LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("EEEE, MMM dd")));
        return "admin/menu";
    }
    
    @PostMapping("/menu/update")
    @ResponseBody
    public ResponseEntity<?> updateMenu(
            @AuthenticationPrincipal OAuth2User user,
            @RequestBody Map<String, Object> body) {
        
        try {
            boolean foodAvailable = (Boolean) body.getOrDefault("foodAvailable", false);
            boolean vegAvailable = (Boolean) body.getOrDefault("vegAvailable", false);
            boolean nonvegAvailable = (Boolean) body.getOrDefault("nonvegAvailable", false);
            List<String> vegItems = (List<String>) body.getOrDefault("vegItems", List.of());
            List<String> nonvegItems = (List<String>) body.getOrDefault("nonvegItems", List.of());
            
            String updatedBy = user.getAttribute("Email");
            
            foodPoolService.updateMenu(LocalDate.now(), foodAvailable, vegAvailable, nonvegAvailable,
                    vegItems, nonvegItems, updatedBy);
            
            return ResponseEntity.ok(Map.of("success", true, "message", "Menu updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    // ============== EMPLOYEE MANAGEMENT (ADMINISTRATOR ONLY) ==============
    
    @GetMapping("/employees")
    public String employees(@AuthenticationPrincipal OAuth2User user, Model model) {
        addCommonAttributes(user, model);
        model.addAttribute("employees", employeeService.getAllActiveEmployees());
        model.addAttribute("administrators", employeeService.getAdministrators());
        model.addAttribute("contributors", employeeService.getContributors());
        return "admin/employees";
    }
    
    @PostMapping("/employees/upload")
    @ResponseBody
    public ResponseEntity<?> uploadEmployees(@RequestParam("file") MultipartFile file) {
        try {
            int count = employeeService.loadFromExcel(file.getInputStream());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Loaded " + count + " employees"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/employees/add")
    @ResponseBody
    public ResponseEntity<?> addEmployee(@RequestBody Map<String, Object> body) {
        try {
            String employeeId = (String) body.get("employeeId");
            String name = (String) body.get("name");
            String email = (String) body.get("email");
            String role = (String) body.getOrDefault("role", "USER");
            
            Employee emp = employeeService.addEmployeeWithRole(employeeId, name, email, role);
            return ResponseEntity.ok(Map.of("success", true, "message", "Employee added: " + emp.getName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/employees/{employeeId}/role")
    @ResponseBody
    public ResponseEntity<?> setRole(
            @PathVariable String employeeId,
            @RequestBody Map<String, String> body) {
        
        String role = body.getOrDefault("role", "USER");
        
        if (!role.equals("ADMINISTRATOR") && !role.equals("CONTRIBUTOR") && !role.equals("USER")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role"));
        }
        
        employeeService.setRole(employeeId, role);
        return ResponseEntity.ok(Map.of("success", true, "message", "Role updated to " + role));
    }
    
    @PostMapping("/employees/{employeeId}/admin")
    @ResponseBody
    public ResponseEntity<?> setAdmin(
            @PathVariable String employeeId,
            @RequestBody Map<String, Boolean> body) {
        
        boolean isAdmin = body.getOrDefault("isAdmin", false);
        employeeService.setAdmin(employeeId, isAdmin);
        return ResponseEntity.ok(Map.of("success", true));
    }
    
    @PostMapping("/employees/{employeeId}/status")
    @ResponseBody
    public ResponseEntity<?> setStatus(
            @PathVariable String employeeId,
            @RequestBody Map<String, Boolean> body) {
        
        boolean isActive = body.getOrDefault("isActive", true);
        employeeService.setActiveStatus(employeeId, isActive);
        return ResponseEntity.ok(Map.of("success", true));
    }
    
    @DeleteMapping("/employees/{employeeId}")
    @ResponseBody
    public ResponseEntity<?> deleteEmployee(@PathVariable String employeeId) {
        employeeService.deleteEmployee(employeeId);
        return ResponseEntity.ok(Map.of("success", true));
    }
    
    @GetMapping("/pools/{date}")
    @ResponseBody
    public ResponseEntity<?> getPoolsByDate(@PathVariable String date) {
        try {
            LocalDate localDate = LocalDate.parse(date);
            List<FoodPool> pools = foodPoolService.getPoolsForFoodDate(localDate);
            return ResponseEntity.ok(pools);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
