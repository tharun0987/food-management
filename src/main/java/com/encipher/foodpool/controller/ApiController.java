package com.encipher.foodpool.controller;

import com.encipher.foodpool.model.*;
import com.encipher.foodpool.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ApiController {
    
    private final EmployeeService employeeService;
    private final FoodPoolService foodPoolService;
    private final GraceRequestService graceRequestService;
    private final QrCodeService qrCodeService;
    
    @PostMapping("/pool")
    public ResponseEntity<?> submitPool(
            @AuthenticationPrincipal OAuth2User user,
            @RequestBody Map<String, String> body) {
        
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }
        
        String employeeId = user.getAttribute("employeeId");
        String foodType = body.get("foodType");
        
        if (foodType == null || (!foodType.equals("veg") && !foodType.equals("nonveg"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid food type"));
        }
        
        try {
            Employee employee = employeeService.findByEmployeeId(employeeId)
                    .orElseThrow(() -> new Exception("Employee not found"));
            
            FoodPool pool = foodPoolService.registerPool(employee, foodType);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Successfully registered for " + foodType.toUpperCase() + " meal!",
                    "foodType", foodType
            ));
        } catch (Exception e) {
            log.error("Pool error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/scan")
    public ResponseEntity<?> verifyScan(
            @AuthenticationPrincipal OAuth2User user,
            @RequestBody Map<String, String> body) {
        
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }
        
        String employeeId = user.getAttribute("employeeId");
        String qrData = body.get("qrData");
        
        try {
            Employee employee = employeeService.findByEmployeeId(employeeId)
                    .orElseThrow(() -> new Exception("Employee not found"));
            
            FoodScan scan = foodPoolService.recordScan(employee, qrData);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Food collected! (" + scan.getFoodType().toUpperCase() + ")",
                    "foodType", scan.getFoodType()
            ));
        } catch (Exception e) {
            log.error("Scan error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@AuthenticationPrincipal OAuth2User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }
        
        String employeeId = user.getAttribute("employeeId");
        LocalDate today = LocalDate.now();
        
        // Check pool status for current survey's food date
        boolean pooledForSurvey = foodPoolService.hasPooledForCurrentSurvey(employeeId);
        
        // Check collection status for today (if today is a food day)
        boolean collected = foodPoolService.hasCollectedToday(employeeId);
        
        // Check if pooled for today as food date (for collection)
        boolean pooledForToday = foodPoolService.hasPooledForFoodDate(employeeId, today);
        
        MenuConfig menu = foodPoolService.getTodayMenu();
        
        String foodType = null;
        if (pooledForSurvey) {
            foodType = foodPoolService.getPoolForCurrentSurvey(employeeId)
                    .map(FoodPool::getFoodType).orElse(null);
        }
        
        // Check grace period status
        LocalDate surveyFoodDate = menu.getFoodDate() != null ? menu.getFoodDate() : today.plusDays(1);
        boolean canSubmitGraceRequest = graceRequestService.canSubmitGraceRequest(employeeId, surveyFoodDate);
        boolean inGracePeriod = graceRequestService.isWithinGracePeriod(surveyFoodDate);
        
        return ResponseEntity.ok(Map.of(
                "pooled", pooledForSurvey,
                "pooledForToday", pooledForToday,
                "collected", collected,
                "foodType", foodType != null ? foodType : "",
                "vegAvailable", menu.isVegAvailable(),
                "nonvegAvailable", menu.isNonvegAvailable(),
                "poolOpen", menu.isPoolOpen(),
                "isFoodCollectionDay", foodPoolService.isFoodCollectionDay(),
                "inGracePeriod", inGracePeriod,
                "canSubmitGraceRequest", canSubmitGraceRequest
        ));
    }
    
    // ============== GRACE REQUESTS ==============
    
    @PostMapping("/grace/request")
    public ResponseEntity<?> submitGraceRequest(
            @AuthenticationPrincipal OAuth2User user,
            @RequestBody Map<String, String> body) {
        
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }
        
        String employeeId = user.getAttribute("employeeId");
        String foodType = body.get("foodType");
        String reason = body.getOrDefault("reason", "");
        String foodDateStr = body.get("foodDate");
        
        if (foodType == null || (!foodType.equals("veg") && !foodType.equals("nonveg"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid food type"));
        }
        
        try {
            Employee employee = employeeService.findByEmployeeId(employeeId)
                    .orElseThrow(() -> new Exception("Employee not found"));
            
            LocalDate foodDate;
            if (foodDateStr != null && !foodDateStr.isEmpty()) {
                foodDate = LocalDate.parse(foodDateStr);
            } else {
                MenuConfig menu = foodPoolService.getTodayMenu();
                foodDate = menu.getFoodDate() != null ? menu.getFoodDate() : LocalDate.now().plusDays(1);
            }
            
            GraceRequest request = graceRequestService.submitGraceRequest(employee, foodDate, foodType, reason);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Grace request submitted successfully. Waiting for admin approval.",
                    "request", request
            ));
        } catch (Exception e) {
            log.error("Grace request error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/grace/my-requests")
    public ResponseEntity<?> getMyGraceRequests(@AuthenticationPrincipal OAuth2User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }
        
        String employeeId = user.getAttribute("employeeId");
        return ResponseEntity.ok(graceRequestService.getRequestsByEmployee(employeeId));
    }
    
    // ============== QR VALIDATION ==============
    
    @PostMapping("/qr/validate")
    public ResponseEntity<?> validateQrCode(@RequestBody Map<String, String> body) {
        String qrData = body.get("qrData");
        
        if (qrData == null || qrData.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "error", "No QR data provided"));
        }
        
        QrCode qrCode = qrCodeService.validateQrCode(qrData);
        
        if (qrCode == null) {
            return ResponseEntity.ok(Map.of("valid", false, "error", "Invalid or expired QR code"));
        }
        
        boolean isForToday = qrCode.getFoodDate().equals(LocalDate.now());
        
        return ResponseEntity.ok(Map.of(
                "valid", true,
                "isForToday", isForToday,
                "foodDate", qrCode.getFoodDate().toString()
        ));
    }
}
