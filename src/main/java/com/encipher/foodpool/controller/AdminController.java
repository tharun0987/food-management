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
import java.util.Optional;

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
        LocalDate selectedDate = today;
        
        // Parse view date if provided
        if (viewDate != null && !viewDate.isEmpty()) {
            try {
                selectedDate = LocalDate.parse(viewDate);
            } catch (Exception e) {
                selectedDate = today;
            }
        }
        
        // Today's menu and pool status (for survey control)
        MenuConfig menu = foodPoolService.getTodayMenu();
        model.addAttribute("menu", menu);
        model.addAttribute("poolOpen", menu.isPoolOpen());
        model.addAttribute("foodAvailable", menu.isFoodAvailable());
        
        // Food date from today's survey (if any)
        LocalDate surveyFoodDate = menu.getFoodDate() != null ? menu.getFoodDate() : today.plusDays(1);
        model.addAttribute("surveyFoodDate", surveyFoodDate.format(DateTimeFormatter.ofPattern("EEEE, MMM dd")));
        model.addAttribute("surveyFoodDateValue", surveyFoodDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        
        // Check if today is a food collection day
        boolean isFoodCollectionDay = foodPoolService.isFoodCollectionDay();
        model.addAttribute("isFoodCollectionDay", isFoodCollectionDay);
        
        // Pool time info
        if (menu.isPoolOpen() && menu.getPoolAutoCloseAt() != null) {
            long minutesLeft = ChronoUnit.MINUTES.between(LocalDateTime.now(), menu.getPoolAutoCloseAt());
            model.addAttribute("minutesLeft", Math.max(0, minutesLeft));
            model.addAttribute("autoCloseTime", menu.getPoolAutoCloseAt().format(DateTimeFormatter.ofPattern("hh:mm a")));
        }
        
        // Determine which food date to show stats for
        // If viewing a specific date, use that
        // Otherwise, if today is food collection day, show today's stats
        // Otherwise, show stats for survey food date
        LocalDate statsDate;
        if (viewDate != null && !viewDate.isEmpty()) {
            statsDate = selectedDate;
        } else if (isFoodCollectionDay) {
            statsDate = today;
        } else {
            statsDate = surveyFoodDate;
        }
        
        model.addAttribute("statsDate", statsDate);
        model.addAttribute("statsDateDisplay", statsDate.format(DateTimeFormatter.ofPattern("EEEE, MMM dd")));
        model.addAttribute("viewDate", statsDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        
        // Get stats for the selected food date
        Map<String, Long> stats = foodPoolService.getStatsForFoodDate(statsDate);
        model.addAttribute("vegCount", stats.get("veg"));
        model.addAttribute("nonvegCount", stats.get("nonveg"));
        model.addAttribute("totalCount", stats.get("total"));
        model.addAttribute("collectedCount", stats.get("collected"));
        model.addAttribute("collectedWithVote", stats.getOrDefault("collectedWithVote", 0L));
        model.addAttribute("collectedWithoutVote", stats.getOrDefault("collectedWithoutVote", 0L));
        
        long total = stats.get("total");
        long collected = stats.get("collected");
        long notCollected = total - collected;
        model.addAttribute("consumptionPercent", total > 0 ? (collected * 100 / total) : 0);
        model.addAttribute("notCollectedCount", Math.max(0, notCollected));
        
        long totalEmployees = employeeService.getAllActiveEmployees().size();
        long notVoted = totalEmployees - total;
        model.addAttribute("totalEmployees", totalEmployees);
        model.addAttribute("notVotedCount", Math.max(0, notVoted));
        model.addAttribute("participationPercent", totalEmployees > 0 ? (total * 100 / totalEmployees) : 0);
        
        // Get pools and scans for the stats date
        model.addAttribute("pools", foodPoolService.getPoolsForFoodDate(statsDate));
        model.addAttribute("scans", foodPoolService.getScansForFoodDate(statsDate));
        
        model.addAttribute("today", today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        model.addAttribute("todayDisplay", today.format(DateTimeFormatter.ofPattern("EEEE, MMM dd")));
        
        // Date navigation
        model.addAttribute("prevWeek", statsDate.minusWeeks(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        model.addAttribute("nextWeek", statsDate.plusWeeks(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        model.addAttribute("yesterday", statsDate.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        model.addAttribute("tomorrow", statsDate.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        
        return "admin/dashboard";
    }
    
    // API to get stats for a specific date (for AJAX)
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
                    "message", "Pool opened for " + duration + " hours! Food will be served on " + 
                              foodDate.format(DateTimeFormatter.ofPattern("MMM dd"))
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
            return ResponseEntity.ok(Map.of("success", true, "message", "Pool closed."));
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
            return ResponseEntity.ok(Map.of("success", true, "message", "Reminder sent to participate in pool!"));
        } catch (Exception e) {
            log.error("Failed to send participate reminder: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to send notification: " + e.getMessage()));
        }
    }
    
    @PostMapping("/notify/eat")
    @ResponseBody
    public ResponseEntity<?> sendEatReminder(@AuthenticationPrincipal OAuth2User user) {
        try {
            LocalDate today = LocalDate.now();
            Map<String, Long> stats = foodPoolService.getStatsForFoodDate(today);
            cliqNotificationService.sendEatReminder(stats.get("total") - stats.get("collected"));
            return ResponseEntity.ok(Map.of("success", true, "message", "Reminder sent to collect food!"));
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
            return ResponseEntity.ok(Map.of("success", true, "message", "Last call reminder sent!"));
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
            return ResponseEntity.ok(Map.of("success", true, "message", "Custom notification sent!"));
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
            
            return ResponseEntity.ok(Map.of("success", true, "message", "Menu updated successfully!"));
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
