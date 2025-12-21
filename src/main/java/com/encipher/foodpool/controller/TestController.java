package com.encipher.foodpool.controller;

import com.encipher.foodpool.model.*;
import com.encipher.foodpool.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Test controller for verifying API responses
 * Remove or secure in production
 */
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
@Slf4j
public class TestController {
    
    private final EmployeeService employeeService;
    private final FoodPoolService foodPoolService;
    
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "status", "OK",
            "timestamp", LocalDate.now().toString()
        ));
    }
    
    @GetMapping("/data-check")
    public ResponseEntity<?> dataCheck() {
        try {
            LocalDate today = LocalDate.now();
            
            // Employee count
            List<Employee> employees = employeeService.getAllActiveEmployees();
            
            // Menu config
            MenuConfig menu = foodPoolService.getTodayMenu();
            LocalDate surveyFoodDate = menu.getFoodDate() != null ? menu.getFoodDate() : today.plusDays(1);
            
            // Survey stats
            Map<String, Long> surveyStats = foodPoolService.getStatsForFoodDate(surveyFoodDate);
            List<FoodPool> surveyPools = foodPoolService.getPoolsForFoodDate(surveyFoodDate);
            
            // Collection stats
            boolean isFoodCollectionDay = foodPoolService.isFoodCollectionDay();
            Map<String, Long> collectionStats = foodPoolService.getTodayCollectionStats();
            List<FoodScan> todayScans = foodPoolService.getTodayScans();
            List<FoodPool> todayPools = foodPoolService.getPoolsForFoodDate(today);
            
            // Build response
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("today", today.toString());
            response.put("totalEmployees", employees.size());
            
            // Menu info
            Map<String, Object> menuInfo = new LinkedHashMap<>();
            menuInfo.put("foodAvailable", menu.isFoodAvailable());
            menuInfo.put("poolOpen", menu.isPoolOpen());
            menuInfo.put("surveyFoodDate", surveyFoodDate.toString());
            menuInfo.put("surveyFoodDateFormatted", surveyFoodDate.format(DateTimeFormatter.ofPattern("EEEE, MMM dd")));
            if (menu.getPoolAutoCloseAt() != null) {
                menuInfo.put("autoCloseAt", menu.getPoolAutoCloseAt().toString());
            }
            response.put("menuConfig", menuInfo);
            
            // Survey data
            Map<String, Object> surveyInfo = new LinkedHashMap<>();
            surveyInfo.put("vegCount", surveyStats.get("veg"));
            surveyInfo.put("nonvegCount", surveyStats.get("nonveg"));
            surveyInfo.put("totalVoted", surveyStats.get("total"));
            surveyInfo.put("poolsCount", surveyPools.size());
            surveyInfo.put("poolsList", surveyPools.stream()
                .map(p -> Map.of(
                    "name", p.getEmployeeName(),
                    "choice", p.getFoodType(),
                    "foodDate", p.getFoodDate() != null ? p.getFoodDate().toString() : "null"
                ))
                .toList());
            response.put("surveyData", surveyInfo);
            
            // Collection data
            Map<String, Object> collectionInfo = new LinkedHashMap<>();
            collectionInfo.put("isFoodCollectionDay", isFoodCollectionDay);
            collectionInfo.put("todayPoolsCount", todayPools.size());
            collectionInfo.put("vegVoted", collectionStats.get("veg"));
            collectionInfo.put("nonvegVoted", collectionStats.get("nonveg"));
            collectionInfo.put("totalVoted", collectionStats.get("total"));
            collectionInfo.put("collected", collectionStats.get("collected"));
            collectionInfo.put("scansCount", todayScans.size());
            collectionInfo.put("scansList", todayScans.stream()
                .map(s -> Map.of(
                    "name", s.getEmployeeName(),
                    "foodType", s.getFoodType() != null ? s.getFoodType() : "unknown",
                    "didVote", s.isDidVote(),
                    "foodDate", s.getFoodDate() != null ? s.getFoodDate().toString() : "null"
                ))
                .toList());
            response.put("collectionData", collectionInfo);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Data check error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/date-range/{startDate}/{endDate}")
    public ResponseEntity<?> dateRangeCheck(
            @PathVariable String startDate,
            @PathVariable String endDate) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            
            List<FoodPool> pools = foodPoolService.getPoolsForDateRange(start, end);
            List<FoodScan> scans = foodPoolService.getScansForDateRange(start, end);
            
            // Group by date
            Map<LocalDate, Long> poolsByDate = new TreeMap<>();
            Map<LocalDate, Long> scansByDate = new TreeMap<>();
            
            for (FoodPool p : pools) {
                if (p.getFoodDate() != null) {
                    poolsByDate.merge(p.getFoodDate(), 1L, Long::sum);
                }
            }
            for (FoodScan s : scans) {
                if (s.getFoodDate() != null) {
                    scansByDate.merge(s.getFoodDate(), 1L, Long::sum);
                }
            }
            
            return ResponseEntity.ok(Map.of(
                "dateRange", startDate + " to " + endDate,
                "totalPools", pools.size(),
                "totalScans", scans.size(),
                "poolsByDate", poolsByDate,
                "scansByDate", scansByDate
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

