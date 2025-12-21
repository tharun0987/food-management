package com.encipher.foodpool.controller;

import com.encipher.foodpool.model.*;
import com.encipher.foodpool.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

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
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Model model) {
        addCommonAttributes(user, model);
        
        LocalDate today = LocalDate.now();
        
        // Survey Control
        MenuConfig menu = foodPoolService.getTodayMenu();
        model.addAttribute("menu", menu);
        model.addAttribute("poolOpen", menu.isPoolOpen());
        model.addAttribute("foodAvailable", menu.isFoodAvailable());
        
        LocalDate surveyFoodDate = menu.getFoodDate() != null ? menu.getFoodDate() : today.plusDays(1);
        model.addAttribute("surveyFoodDate", surveyFoodDate);
        model.addAttribute("surveyFoodDateFormatted", surveyFoodDate.format(DateTimeFormatter.ofPattern("EEEE, MMM dd")));
        
        if (menu.isPoolOpen() && menu.getPoolAutoCloseAt() != null) {
            long minutesLeft = ChronoUnit.MINUTES.between(LocalDateTime.now(), menu.getPoolAutoCloseAt());
            model.addAttribute("minutesLeft", Math.max(0, minutesLeft));
            model.addAttribute("autoCloseTime", menu.getPoolAutoCloseAt().format(DateTimeFormatter.ofPattern("hh:mm a")));
        }
        
        // Today's Collection (if food day)
        boolean isFoodCollectionDay = foodPoolService.isFoodCollectionDay();
        model.addAttribute("isFoodCollectionDay", isFoodCollectionDay);
        
        if (isFoodCollectionDay) {
            Map<String, Long> collectionStats = foodPoolService.getTodayCollectionStats();
            model.addAttribute("collectionVegCount", collectionStats.get("veg"));
            model.addAttribute("collectionNonvegCount", collectionStats.get("nonveg"));
            model.addAttribute("collectionTotalVoted", collectionStats.get("total"));
            model.addAttribute("collectionCollected", collectionStats.get("collected"));
            model.addAttribute("collectionWithVote", collectionStats.getOrDefault("collectedWithVote", 0L));
            model.addAttribute("collectionWithoutVote", collectionStats.getOrDefault("collectedWithoutVote", 0L));
            
            List<FoodPool> collectionPools = foodPoolService.getPoolsForFoodDate(today);
            List<FoodScan> collectionScans = foodPoolService.getTodayScans();
            model.addAttribute("collectionPools", collectionPools);
            model.addAttribute("collectionScans", collectionScans);
            
            // List of employee IDs who have collected
            List<String> collectedEmployeeIds = collectionScans.stream()
                    .map(FoodScan::getEmployeeId)
                    .collect(Collectors.toList());
            model.addAttribute("collectedEmployeeIds", collectedEmployeeIds);
        }
        
        // Current Survey Stats
        Map<String, Long> surveyStats = foodPoolService.getStatsForFoodDate(surveyFoodDate);
        model.addAttribute("surveyVegCount", surveyStats.get("veg"));
        model.addAttribute("surveyNonvegCount", surveyStats.get("nonveg"));
        model.addAttribute("surveyTotalVoted", surveyStats.get("total"));
        model.addAttribute("surveyPools", foodPoolService.getPoolsForFoodDate(surveyFoodDate));
        
        long totalEmployees = employeeService.getAllActiveEmployees().size();
        model.addAttribute("totalEmployees", totalEmployees);
        
        long surveyTotal = surveyStats.get("total");
        model.addAttribute("surveyNotVoted", Math.max(0, totalEmployees - surveyTotal));
        
        // Date Range Report - Optimized
        LocalDate start = today.minusDays(30);
        LocalDate end = today;
        
        if (startDate != null && !startDate.isEmpty()) {
            try { start = LocalDate.parse(startDate); } catch (Exception e) {}
        }
        if (endDate != null && !endDate.isEmpty()) {
            try { end = LocalDate.parse(endDate); } catch (Exception e) {}
        }
        
        model.addAttribute("startDate", start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        model.addAttribute("endDate", end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        
        // Generate optimized report
        Map<String, Object> report = generateOptimizedReport(start, end, totalEmployees);
        model.addAttribute("report", report);
        
        model.addAttribute("today", today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        model.addAttribute("todayDisplay", today.format(DateTimeFormatter.ofPattern("EEEE, MMM dd")));
        model.addAttribute("tomorrowDate", today.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        
        return "admin/dashboard";
    }
    
    // ============== OPTIMIZED REPORT GENERATION ==============
    
    private Map<String, Object> generateOptimizedReport(LocalDate start, LocalDate end, long totalEmployees) {
        // Fetch all data in ONE query each
        List<FoodPool> allPools = foodPoolService.getPoolsForDateRange(start, end);
        List<FoodScan> allScans = foodPoolService.getScansForDateRange(start, end);
        
        // Group by food date
        Map<LocalDate, List<FoodPool>> poolsByDate = allPools.stream()
                .filter(p -> p.getFoodDate() != null)
                .collect(Collectors.groupingBy(FoodPool::getFoodDate));
        
        Map<LocalDate, List<FoodScan>> scansByDate = allScans.stream()
                .filter(s -> s.getFoodDate() != null)
                .collect(Collectors.groupingBy(FoodScan::getFoodDate));
        
        // Get all unique dates with activity
        Set<LocalDate> allDates = new TreeSet<>();
        allDates.addAll(poolsByDate.keySet());
        allDates.addAll(scansByDate.keySet());
        
        // Calculate stats
        int voteDays = poolsByDate.size();
        int collectionDays = scansByDate.size();
        
        long totalVoted = allPools.size();
        long totalVeg = allPools.stream().filter(p -> "veg".equals(p.getFoodType())).count();
        long totalNonveg = allPools.stream().filter(p -> "nonveg".equals(p.getFoodType())).count();
        long totalCollected = allScans.size();
        long totalCollectedWithVote = allScans.stream().filter(FoodScan::isDidVote).count();
        long totalCollectedWithoutVote = allScans.stream().filter(s -> !s.isDidVote()).count();
        
        // Daily breakdown
        List<Map<String, Object>> dailyData = new ArrayList<>();
        for (LocalDate date : allDates) {
            List<FoodPool> dayPools = poolsByDate.getOrDefault(date, Collections.emptyList());
            List<FoodScan> dayScans = scansByDate.getOrDefault(date, Collections.emptyList());
            
            long dayVeg = dayPools.stream().filter(p -> "veg".equals(p.getFoodType())).count();
            long dayNonveg = dayPools.stream().filter(p -> "nonveg".equals(p.getFoodType())).count();
            long dayVoted = dayPools.size();
            long dayCollected = dayScans.size();
            long dayCollectedWithVote = dayScans.stream().filter(FoodScan::isDidVote).count();
            long dayCollectedWithoutVote = dayScans.stream().filter(s -> !s.isDidVote()).count();
            
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            dayData.put("dateDisplay", date.format(DateTimeFormatter.ofPattern("MMM dd, EEE")));
            dayData.put("veg", dayVeg);
            dayData.put("nonveg", dayNonveg);
            dayData.put("totalVoted", dayVoted);
            dayData.put("notVoted", Math.max(0, totalEmployees - dayVoted));
            dayData.put("collected", dayCollected);
            dayData.put("collectedWithVote", dayCollectedWithVote);
            dayData.put("collectedWithoutVote", dayCollectedWithoutVote);
            dayData.put("notCollected", Math.max(0, dayVoted - dayCollected));
            dayData.put("isVoteDay", !dayPools.isEmpty());
            dayData.put("isCollectionDay", !dayScans.isEmpty());
            
            dailyData.add(dayData);
        }
        
        Map<String, Object> report = new HashMap<>();
        report.put("startDate", start.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        report.put("endDate", end.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        report.put("totalDays", ChronoUnit.DAYS.between(start, end) + 1);
        report.put("foodPoolDays", allDates.size());
        report.put("voteDays", voteDays);
        report.put("collectionDays", collectionDays);
        report.put("totalEmployees", totalEmployees);
        report.put("totalVoted", totalVoted);
        report.put("totalVeg", totalVeg);
        report.put("totalNonveg", totalNonveg);
        report.put("totalCollected", totalCollected);
        report.put("totalCollectedWithVote", totalCollectedWithVote);
        report.put("totalCollectedWithoutVote", totalCollectedWithoutVote);
        report.put("totalNotCollected", Math.max(0, totalVoted - totalCollected));
        report.put("avgParticipation", voteDays > 0 ? (totalVoted / voteDays) : 0);
        report.put("avgCollection", collectionDays > 0 ? (totalCollected / collectionDays) : 0);
        report.put("dailyData", dailyData);
        
        return report;
    }
    
    @GetMapping("/reports/data")
    @ResponseBody
    public ResponseEntity<?> getReportData(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            long totalEmployees = employeeService.getAllActiveEmployees().size();
            Map<String, Object> report = generateOptimizedReport(start, end, totalEmployees);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    // ============== SIMPLE EXPORTS ==============
    
    @GetMapping("/export/survey")
    public ResponseEntity<byte[]> exportSurvey() {
        try {
            MenuConfig menu = foodPoolService.getTodayMenu();
            LocalDate foodDate = menu.getFoodDate() != null ? menu.getFoodDate() : LocalDate.now().plusDays(1);
            List<FoodPool> pools = foodPoolService.getPoolsForFoodDate(foodDate);
            
            byte[] excelData = generateSurveyExcel(pools, foodDate);
            String filename = "food_order_" + foodDate + ".xlsx";
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excelData);
        } catch (Exception e) {
            log.error("Error exporting survey: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    @GetMapping("/export/collection")
    public ResponseEntity<byte[]> exportCollection() {
        try {
            LocalDate today = LocalDate.now();
            List<FoodPool> pools = foodPoolService.getPoolsForFoodDate(today);
            List<FoodScan> scans = foodPoolService.getTodayScans();
            
            byte[] excelData = generateCollectionExcel(pools, scans, today);
            String filename = "food_collection_" + today + ".xlsx";
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excelData);
        } catch (Exception e) {
            log.error("Error exporting collection: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    private byte[] generateSurveyExcel(List<FoodPool> pools, LocalDate foodDate) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            
            // Summary
            Sheet summarySheet = workbook.createSheet("Order Summary");
            int row = 0;
            createRow(summarySheet, row++, null, "Food Order for " + foodDate.format(DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy")));
            row++;
            
            long vegCount = pools.stream().filter(p -> "veg".equals(p.getFoodType())).count();
            long nonvegCount = pools.stream().filter(p -> "nonveg".equals(p.getFoodType())).count();
            
            createRow(summarySheet, row++, null, "Vegetarian", String.valueOf(vegCount));
            createRow(summarySheet, row++, null, "Non-Vegetarian", String.valueOf(nonvegCount));
            createRow(summarySheet, row++, null, "TOTAL", String.valueOf(pools.size()));
            
            summarySheet.autoSizeColumn(0);
            summarySheet.autoSizeColumn(1);
            
            // All Orders
            Sheet ordersSheet = workbook.createSheet("All Orders");
            Row header = ordersSheet.createRow(0);
            String[] headers = {"Name", "Choice"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            int rowNum = 1;
            for (FoodPool pool : pools) {
                Row r = ordersSheet.createRow(rowNum++);
                r.createCell(0).setCellValue(pool.getEmployeeName());
                r.createCell(1).setCellValue(pool.getFoodType() != null ? pool.getFoodType().toUpperCase() : "");
            }
            
            ordersSheet.autoSizeColumn(0);
            ordersSheet.autoSizeColumn(1);
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
    
    private byte[] generateCollectionExcel(List<FoodPool> pools, List<FoodScan> scans, LocalDate date) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            
            Set<String> collectedIds = scans.stream().map(FoodScan::getEmployeeId).collect(Collectors.toSet());
            
            // Summary
            Sheet summarySheet = workbook.createSheet("Summary");
            int row = 0;
            createRow(summarySheet, row++, null, "Food Collection - " + date.format(DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy")));
            row++;
            createRow(summarySheet, row++, null, "Total Expected", String.valueOf(pools.size()));
            createRow(summarySheet, row++, null, "Collected", String.valueOf(scans.size()));
            createRow(summarySheet, row++, null, "Not Collected", String.valueOf(pools.size() - collectedIds.size()));
            createRow(summarySheet, row++, null, "Collected Without Vote", String.valueOf(scans.stream().filter(s -> !s.isDidVote()).count()));
            
            summarySheet.autoSizeColumn(0);
            summarySheet.autoSizeColumn(1);
            
            // Collected
            Sheet collectedSheet = workbook.createSheet("Collected");
            Row h1 = collectedSheet.createRow(0);
            h1.createCell(0).setCellValue("Name"); h1.getCell(0).setCellStyle(headerStyle);
            h1.createCell(1).setCellValue("Status"); h1.getCell(1).setCellStyle(headerStyle);
            
            int r1 = 1;
            for (FoodScan scan : scans) {
                Row r = collectedSheet.createRow(r1++);
                r.createCell(0).setCellValue(scan.getEmployeeName());
                r.createCell(1).setCellValue(scan.isDidVote() ? "Voted" : "Did Not Vote");
            }
            collectedSheet.autoSizeColumn(0);
            collectedSheet.autoSizeColumn(1);
            
            // Not Collected
            Sheet notCollectedSheet = workbook.createSheet("Not Collected");
            Row h2 = notCollectedSheet.createRow(0);
            h2.createCell(0).setCellValue("Name"); h2.getCell(0).setCellStyle(headerStyle);
            h2.createCell(1).setCellValue("Choice"); h2.getCell(1).setCellStyle(headerStyle);
            
            int r2 = 1;
            for (FoodPool pool : pools) {
                if (!collectedIds.contains(pool.getEmployeeId())) {
                    Row r = notCollectedSheet.createRow(r2++);
                    r.createCell(0).setCellValue(pool.getEmployeeName());
                    r.createCell(1).setCellValue(pool.getFoodType() != null ? pool.getFoodType().toUpperCase() : "");
                }
            }
            notCollectedSheet.autoSizeColumn(0);
            notCollectedSheet.autoSizeColumn(1);
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
    
    @GetMapping("/reports/export")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            
            byte[] excelData = generateDetailedExcelReport(start, end);
            
            String filename = "food_pool_report_" + startDate + "_to_" + endDate + ".xlsx";
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excelData);
        } catch (Exception e) {
            log.error("Error generating report: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    private byte[] generateDetailedExcelReport(LocalDate start, LocalDate end) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            // Styles
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            
            // Fetch all data in batch
            List<FoodPool> allPools = foodPoolService.getPoolsForDateRange(start, end);
            List<FoodScan> allScans = foodPoolService.getScansForDateRange(start, end);
            List<Employee> allEmployees = employeeService.getAllActiveEmployees();
            
            // Create email lookup from employees
            Map<String, String> emailLookup = allEmployees.stream()
                    .collect(Collectors.toMap(Employee::getEmployeeId, e -> e.getEmail() != null ? e.getEmail() : "", (a, b) -> a));
            
            // Group by date
            Map<LocalDate, List<FoodPool>> poolsByDate = allPools.stream()
                    .filter(p -> p.getFoodDate() != null)
                    .collect(Collectors.groupingBy(FoodPool::getFoodDate));
            
            Map<LocalDate, List<FoodScan>> scansByDate = allScans.stream()
                    .filter(s -> s.getFoodDate() != null)
                    .collect(Collectors.groupingBy(FoodScan::getFoodDate));
            
            Set<LocalDate> allDates = new TreeSet<>();
            allDates.addAll(poolsByDate.keySet());
            allDates.addAll(scansByDate.keySet());
            
            // ============== SHEET 1: DAILY SUMMARY ==============
            Sheet dailySheet = workbook.createSheet("Daily Summary");
            Row dailyHeader = dailySheet.createRow(0);
            String[] dailyHeaders = {"Date", "Day", "Veg Voted", "Non-Veg Voted", "Total Voted", "Collected"};
            for (int i = 0; i < dailyHeaders.length; i++) {
                Cell cell = dailyHeader.createCell(i);
                cell.setCellValue(dailyHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            
            int dailyRowNum = 1;
            for (LocalDate date : allDates) {
                List<FoodPool> dayPools = poolsByDate.getOrDefault(date, Collections.emptyList());
                List<FoodScan> dayScans = scansByDate.getOrDefault(date, Collections.emptyList());
                
                Row row = dailySheet.createRow(dailyRowNum++);
                row.createCell(0).setCellValue(date.toString());
                row.createCell(1).setCellValue(date.format(DateTimeFormatter.ofPattern("EEEE")));
                row.createCell(2).setCellValue(dayPools.stream().filter(p -> "veg".equals(p.getFoodType())).count());
                row.createCell(3).setCellValue(dayPools.stream().filter(p -> "nonveg".equals(p.getFoodType())).count());
                row.createCell(4).setCellValue(dayPools.size());
                row.createCell(5).setCellValue(dayScans.size());
            }
            
            for (int i = 0; i < dailyHeaders.length; i++) {
                dailySheet.autoSizeColumn(i);
            }
            
            // ============== SHEET 2: WHO VOTED ==============
            Sheet votesSheet = workbook.createSheet("Who Voted");
            Row votesHeader = votesSheet.createRow(0);
            String[] voteHeaders = {"Date", "Employee ID", "Name", "Email", "Choice"};
            for (int i = 0; i < voteHeaders.length; i++) {
                Cell cell = votesHeader.createCell(i);
                cell.setCellValue(voteHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            
            int voteRowNum = 1;
            for (FoodPool pool : allPools) {
                Row row = votesSheet.createRow(voteRowNum++);
                row.createCell(0).setCellValue(pool.getFoodDate() != null ? pool.getFoodDate().toString() : "");
                row.createCell(1).setCellValue(pool.getEmployeeId());
                row.createCell(2).setCellValue(pool.getEmployeeName());
                // Get email from pool or lookup
                String email = pool.getEmployeeEmail();
                if (email == null || email.isEmpty()) {
                    email = emailLookup.getOrDefault(pool.getEmployeeId(), "");
                }
                row.createCell(3).setCellValue(email);
                row.createCell(4).setCellValue(pool.getFoodType() != null ? pool.getFoodType().toUpperCase() : "");
            }
            
            for (int i = 0; i < voteHeaders.length; i++) {
                votesSheet.autoSizeColumn(i);
            }
            
            // ============== SHEET 3: WHO COLLECTED ==============
            Sheet collectionsSheet = workbook.createSheet("Who Collected");
            Row collectionsHeader = collectionsSheet.createRow(0);
            String[] collectionHeaders = {"Date", "Employee ID", "Name", "Email", "Food Type", "Had Voted"};
            for (int i = 0; i < collectionHeaders.length; i++) {
                Cell cell = collectionsHeader.createCell(i);
                cell.setCellValue(collectionHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            
            int collectionRowNum = 1;
            for (FoodScan scan : allScans) {
                Row row = collectionsSheet.createRow(collectionRowNum++);
                row.createCell(0).setCellValue(scan.getFoodDate() != null ? scan.getFoodDate().toString() : "");
                row.createCell(1).setCellValue(scan.getEmployeeId());
                row.createCell(2).setCellValue(scan.getEmployeeName());
                // Get email from scan or lookup
                String email = scan.getEmployeeEmail();
                if (email == null || email.isEmpty()) {
                    email = emailLookup.getOrDefault(scan.getEmployeeId(), "");
                }
                row.createCell(3).setCellValue(email);
                row.createCell(4).setCellValue(scan.getFoodType() != null ? scan.getFoodType().toUpperCase() : "UNKNOWN");
                row.createCell(5).setCellValue(scan.isDidVote() ? "Yes" : "No");
            }
            
            for (int i = 0; i < collectionHeaders.length; i++) {
                collectionsSheet.autoSizeColumn(i);
            }
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
    
    private void createRow(Sheet sheet, int rowNum, CellStyle style, String... values) {
        Row row = sheet.createRow(rowNum);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i]);
            if (style != null) cell.setCellStyle(style);
        }
    }
    
    // ============== STATS API ==============
    
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
    
    // ============== POOL CONTROL ==============
    
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
    
    // ============== NOTIFICATIONS ==============
    
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
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Failed to send notification: " + e.getMessage()));
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
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Failed to send notification: " + e.getMessage()));
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
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Failed to send notification: " + e.getMessage()));
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
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Message cannot be empty"));
            }
            cliqNotificationService.sendCustomNotification(message);
            return ResponseEntity.ok(Map.of("success", true, "message", "Custom notification sent"));
        } catch (Exception e) {
            log.error("Failed to send custom notification: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Failed to send notification: " + e.getMessage()));
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
            @SuppressWarnings("unchecked")
            List<String> vegItems = (List<String>) body.getOrDefault("vegItems", List.of());
            @SuppressWarnings("unchecked")
            List<String> nonvegItems = (List<String>) body.getOrDefault("nonvegItems", List.of());
            
            String updatedBy = user.getAttribute("Email");
            
            foodPoolService.updateMenu(LocalDate.now(), foodAvailable, vegAvailable, nonvegAvailable,
                    vegItems, nonvegItems, updatedBy);
            
            return ResponseEntity.ok(Map.of("success", true, "message", "Menu updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    // ============== EMPLOYEES ==============
    
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
