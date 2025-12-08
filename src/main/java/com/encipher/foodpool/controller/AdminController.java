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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {
    
    private final EmployeeService employeeService;
    private final FoodPoolService foodPoolService;
    
    @GetMapping("")
    public String dashboard(@AuthenticationPrincipal OAuth2User user, Model model) {
        model.addAttribute("employeeName", user.getAttribute("employeeName"));
        model.addAttribute("email", user.getAttribute("Email"));
        
        // Today's menu and pool status
        MenuConfig menu = foodPoolService.getTodayMenu();
        model.addAttribute("menu", menu);
        model.addAttribute("poolOpen", menu.isPoolOpen());
        model.addAttribute("foodAvailable", menu.isFoodAvailable());
        
        // Today's stats
        Map<String, Long> stats = foodPoolService.getTodayStats();
        model.addAttribute("vegCount", stats.get("veg"));
        model.addAttribute("nonvegCount", stats.get("nonveg"));
        model.addAttribute("totalCount", stats.get("total"));
        model.addAttribute("collectedCount", stats.get("collected"));
        
        // Today's pools
        model.addAttribute("pools", foodPoolService.getTodayPools());
        
        model.addAttribute("today", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        model.addAttribute("todayDisplay", LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM dd")));
        
        return "admin/dashboard";
    }
    
    @PostMapping("/pool/open")
    @ResponseBody
    public ResponseEntity<?> openPool(@AuthenticationPrincipal OAuth2User user) {
        try {
            String email = user.getAttribute("Email");
            foodPoolService.openPool(email);
            return ResponseEntity.ok(Map.of("success", true, "message", "Pool opened! Users can now register."));
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
    
    @GetMapping("/menu")
    public String menuPage(@AuthenticationPrincipal OAuth2User user, Model model) {
        model.addAttribute("employeeName", user.getAttribute("employeeName"));
        model.addAttribute("menu", foodPoolService.getTodayMenu());
        model.addAttribute("today", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        model.addAttribute("todayDisplay", LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM dd")));
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
    
    @GetMapping("/employees")
    public String employees(@AuthenticationPrincipal OAuth2User user, Model model) {
        model.addAttribute("employeeName", user.getAttribute("employeeName"));
        model.addAttribute("employees", employeeService.getAllActiveEmployees());
        model.addAttribute("admins", employeeService.getAdmins());
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
            boolean isAdmin = (Boolean) body.getOrDefault("isAdmin", false);
            
            Employee emp = employeeService.addEmployee(employeeId, name, email, isAdmin);
            return ResponseEntity.ok(Map.of("success", true, "message", "Employee added: " + emp.getName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
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
            List<FoodPool> pools = foodPoolService.getPoolsForDate(localDate);
            return ResponseEntity.ok(pools);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
