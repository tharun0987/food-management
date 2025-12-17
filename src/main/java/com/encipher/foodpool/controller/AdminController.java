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
            
            long collTotal = collectionStats.get("total");
            long collCollected = collectionStats.get("collected");
            model.addAttribute("collectionPercent", collTotal > 0 ? (collCollected * 100 / collTotal) : 0);
            
            model.addAttribute("collectionPools", foodPoolService.getPoolsForFoodDate(today));
            model.addAttribute("collectionScans", foodPoolService.getTodayScans());
        }
        
        Map<String, Long> surveyStats = foodPoolService.getStatsForFoodDate(surveyFoodDate);
        model.addAttribute("surveyVegCount", surveyStats.get("veg"));
        model.addAttribute("surveyNonvegCount", surveyStats.get("nonveg"));
        model.addAttribute("surveyTotalVoted", surveyStats.get("total"));
        model.addAttribute("surveyPools", foodPoolService.getPoolsForFoodDate(surveyFoodDate));
        
        long totalEmployees = employeeService.getAllActiveEmployees().size();
        model.addAttribute("totalEmployees", totalEmployees);
        
        long surveyTotal = surveyStats.get("total");
        model.addAttribute("surveyNotVoted", Math.max(0, totalEmployees - surveyTotal));
        model.addAttribute("surveyParticipationPercent", totalEmployees > 0 ? (surveyTotal * 100 / totalEmployees) : 0);
        
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
        
        Map<String, Long> historyStats = foodPoolService.getStatsForFoodDate(viewingDate);
        model.addAttribute("historyVegCount", historyStats.get("veg"));
        model.addAttribute("historyNonvegCount", historyStats.get("nonveg"));
        model.addAttribute("historyTotalVoted", historyStats.get("total"));
        model.addAttribute("historyCollected", historyStats.get("collected"));
        model.addAttribute("historyWithVote", historyStats.getOrDefault("collectedWithVote", 0L));
        model.addAttribute("historyWithoutVote", historyStats.getOrDefault("collectedWithoutVote", 0L));
        model.addAttribute("historyPools", foodPoolService.getPoolsForFoodDate(viewingDate));
        model.addAttribute("historyScans", foodPoolService.getScansForFoodDate(viewingDate));
        
        model.addAttribute("today", today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        model.addAttribute("todayDisplay", today.format(DateTimeFormatter.ofPattern("EEEE, MMM dd")));
        model.addAttribute("prevDay", viewingDate.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        model.addAttribute("nextDay", viewingDate.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        model.addAttribute("prevWeek", viewingDate.minusWeeks(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        model.addAttribute("nextWeek", viewingDate.plusWeeks(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        
        return "admin/dashboard";
    }
    
    // ============== REPORTS PAGE ==============
    
    @GetMapping("/reports")
    public String reportsPage(
            @AuthenticationPrincipal OAuth2User user,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Model model) {
        addCommonAttributes(user, model);
        
        LocalDate today = LocalDate.now();
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
        model.addAttribute("startDateDisplay", start.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        model.addAttribute("endDateDisplay", end.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        
        // Get report data
        Map<String, Object> report = generateReport(start, end);
        model.addAttribute("report", report);
        
        return "admin/reports";
    }
    
    @GetMapping("/reports/data")
    @ResponseBody
    public ResponseEntity<?> getReportData(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            Map<String, Object> report = generateReport(start, end);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    private Map<String, Object> generateReport(LocalDate start, LocalDate end) {
        long totalEmployees = employeeService.getAllActiveEmployees().size();
        
        int foodPoolDays = 0;
        long totalVoted = 0;
        long totalVeg = 0;
        long totalNonveg = 0;
        long totalCollected = 0;
        long totalCollectedWithVote = 0;
        long totalCollectedWithoutVote = 0;
        long totalNotVoted = 0;
        long totalNotCollected = 0;
        
        List<Map<String, Object>> dailyData = new ArrayList<>();
        
        LocalDate current = start;
        while (!current.isAfter(end)) {
            Map<String, Long> stats = foodPoolService.getStatsForFoodDate(current);
            long dayTotal = stats.get("total");
            long dayCollected = stats.get("collected");
            
            if (dayTotal > 0 || dayCollected > 0) {
                foodPoolDays++;
                totalVoted += dayTotal;
                totalVeg += stats.get("veg");
                totalNonveg += stats.get("nonveg");
                totalCollected += dayCollected;
                totalCollectedWithVote += stats.getOrDefault("collectedWithVote", 0L);
                totalCollectedWithoutVote += stats.getOrDefault("collectedWithoutVote", 0L);
                totalNotVoted += Math.max(0, totalEmployees - dayTotal);
                totalNotCollected += Math.max(0, dayTotal - dayCollected);
                
                Map<String, Object> dayData = new HashMap<>();
                dayData.put("date", current.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                dayData.put("dateDisplay", current.format(DateTimeFormatter.ofPattern("MMM dd, EEE")));
                dayData.put("veg", stats.get("veg"));
                dayData.put("nonveg", stats.get("nonveg"));
                dayData.put("totalVoted", dayTotal);
                dayData.put("notVoted", Math.max(0, totalEmployees - dayTotal));
                dayData.put("collected", dayCollected);
                dayData.put("collectedWithVote", stats.getOrDefault("collectedWithVote", 0L));
                dayData.put("collectedWithoutVote", stats.getOrDefault("collectedWithoutVote", 0L));
                dayData.put("notCollected", Math.max(0, dayTotal - dayCollected));
                dayData.put("participationPercent", totalEmployees > 0 ? (dayTotal * 100 / totalEmployees) : 0);
                dayData.put("collectionPercent", dayTotal > 0 ? (dayCollected * 100 / dayTotal) : 0);
                
                dailyData.add(dayData);
            }
            
            current = current.plusDays(1);
        }
        
        Map<String, Object> report = new HashMap<>();
        report.put("startDate", start.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        report.put("endDate", end.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        report.put("totalDays", ChronoUnit.DAYS.between(start, end) + 1);
        report.put("foodPoolDays", foodPoolDays);
        report.put("totalEmployees", totalEmployees);
        report.put("totalVoted", totalVoted);
        report.put("totalVeg", totalVeg);
        report.put("totalNonveg", totalNonveg);
        report.put("totalNotVoted", totalNotVoted);
        report.put("totalCollected", totalCollected);
        report.put("totalCollectedWithVote", totalCollectedWithVote);
        report.put("totalCollectedWithoutVote", totalCollectedWithoutVote);
        report.put("totalNotCollected", totalNotCollected);
        report.put("avgParticipation", foodPoolDays > 0 ? (totalVoted / foodPoolDays) : 0);
        report.put("avgCollection", foodPoolDays > 0 ? (totalCollected / foodPoolDays) : 0);
        report.put("dailyData", dailyData);
        
        return report;
    }
    
    @GetMapping("/reports/export")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            
            byte[] excelData = generateExcelReport(start, end);
            
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
    
    private byte[] generateExcelReport(LocalDate start, LocalDate end) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            // Create styles
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            
            long totalEmployees = employeeService.getAllActiveEmployees().size();
            
            // Summary Sheet
            Sheet summarySheet = workbook.createSheet("Summary");
            Map<String, Object> report = generateReport(start, end);
            
            int rowNum = 0;
            createSummaryRow(summarySheet, rowNum++, "Food Pool Report", headerStyle);
            createSummaryRow(summarySheet, rowNum++, "");
            createSummaryRow(summarySheet, rowNum++, "Date Range", start + " to " + end);
            createSummaryRow(summarySheet, rowNum++, "Total Days in Range", report.get("totalDays"));
            createSummaryRow(summarySheet, rowNum++, "Days with Food Pool", report.get("foodPoolDays"));
            createSummaryRow(summarySheet, rowNum++, "Total Employees", report.get("totalEmployees"));
            createSummaryRow(summarySheet, rowNum++, "");
            createSummaryRow(summarySheet, rowNum++, "Voting Statistics", headerStyle);
            createSummaryRow(summarySheet, rowNum++, "Total Votes (all days)", report.get("totalVoted"));
            createSummaryRow(summarySheet, rowNum++, "Total Veg Votes", report.get("totalVeg"));
            createSummaryRow(summarySheet, rowNum++, "Total Non-Veg Votes", report.get("totalNonveg"));
            createSummaryRow(summarySheet, rowNum++, "Average Participation per Day", report.get("avgParticipation"));
            createSummaryRow(summarySheet, rowNum++, "");
            createSummaryRow(summarySheet, rowNum++, "Collection Statistics", headerStyle);
            createSummaryRow(summarySheet, rowNum++, "Total Collections (all days)", report.get("totalCollected"));
            createSummaryRow(summarySheet, rowNum++, "Collected (with vote)", report.get("totalCollectedWithVote"));
            createSummaryRow(summarySheet, rowNum++, "Collected (without vote)", report.get("totalCollectedWithoutVote"));
            createSummaryRow(summarySheet, rowNum++, "Total Not Collected", report.get("totalNotCollected"));
            createSummaryRow(summarySheet, rowNum++, "Average Collection per Day", report.get("avgCollection"));
            
            summarySheet.autoSizeColumn(0);
            summarySheet.autoSizeColumn(1);
            
            // Daily Data Sheet
            Sheet dailySheet = workbook.createSheet("Daily Data");
            Row headerRow = dailySheet.createRow(0);
            String[] headers = {"Date", "Day", "Veg Votes", "Non-Veg Votes", "Total Voted", "Not Voted", 
                               "Collected", "Collected (Voted)", "Collected (No Vote)", "Not Collected", 
                               "Participation %", "Collection %"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dailyData = (List<Map<String, Object>>) report.get("dailyData");
            int dataRowNum = 1;
            for (Map<String, Object> day : dailyData) {
                Row row = dailySheet.createRow(dataRowNum++);
                row.createCell(0).setCellValue((String) day.get("date"));
                row.createCell(1).setCellValue((String) day.get("dateDisplay"));
                row.createCell(2).setCellValue(((Number) day.get("veg")).longValue());
                row.createCell(3).setCellValue(((Number) day.get("nonveg")).longValue());
                row.createCell(4).setCellValue(((Number) day.get("totalVoted")).longValue());
                row.createCell(5).setCellValue(((Number) day.get("notVoted")).longValue());
                row.createCell(6).setCellValue(((Number) day.get("collected")).longValue());
                row.createCell(7).setCellValue(((Number) day.get("collectedWithVote")).longValue());
                row.createCell(8).setCellValue(((Number) day.get("collectedWithoutVote")).longValue());
                row.createCell(9).setCellValue(((Number) day.get("notCollected")).longValue());
                row.createCell(10).setCellValue(((Number) day.get("participationPercent")).longValue() + "%");
                row.createCell(11).setCellValue(((Number) day.get("collectionPercent")).longValue() + "%");
            }
            
            for (int i = 0; i < headers.length; i++) {
                dailySheet.autoSizeColumn(i);
            }
            
            // Detailed Votes Sheet
            Sheet votesSheet = workbook.createSheet("All Votes");
            Row votesHeader = votesSheet.createRow(0);
            String[] voteHeaders = {"Date", "Employee ID", "Employee Name", "Food Type", "Vote Time"};
            for (int i = 0; i < voteHeaders.length; i++) {
                Cell cell = votesHeader.createCell(i);
                cell.setCellValue(voteHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            
            int voteRowNum = 1;
            LocalDate current = start;
            while (!current.isAfter(end)) {
                List<FoodPool> pools = foodPoolService.getPoolsForFoodDate(current);
                for (FoodPool pool : pools) {
                    Row row = votesSheet.createRow(voteRowNum++);
                    row.createCell(0).setCellValue(current.toString());
                    row.createCell(1).setCellValue(pool.getEmployeeId());
                    row.createCell(2).setCellValue(pool.getEmployeeName());
                    row.createCell(3).setCellValue(pool.getFoodType().toUpperCase());
                    row.createCell(4).setCellValue(pool.getTimestamp() != null ? 
                            pool.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "");
                }
                current = current.plusDays(1);
            }
            
            for (int i = 0; i < voteHeaders.length; i++) {
                votesSheet.autoSizeColumn(i);
            }
            
            // Detailed Collections Sheet
            Sheet collectionsSheet = workbook.createSheet("All Collections");
            Row collectionsHeader = collectionsSheet.createRow(0);
            String[] collectionHeaders = {"Date", "Employee ID", "Employee Name", "Food Type", "Voted?", "Collection Time"};
            for (int i = 0; i < collectionHeaders.length; i++) {
                Cell cell = collectionsHeader.createCell(i);
                cell.setCellValue(collectionHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            
            int collectionRowNum = 1;
            current = start;
            while (!current.isAfter(end)) {
                List<FoodScan> scans = foodPoolService.getScansForFoodDate(current);
                for (FoodScan scan : scans) {
                    Row row = collectionsSheet.createRow(collectionRowNum++);
                    row.createCell(0).setCellValue(current.toString());
                    row.createCell(1).setCellValue(scan.getEmployeeId());
                    row.createCell(2).setCellValue(scan.getEmployeeName());
                    row.createCell(3).setCellValue(scan.getFoodType() != null ? scan.getFoodType().toUpperCase() : "UNKNOWN");
                    row.createCell(4).setCellValue(scan.isDidVote() ? "Yes" : "No");
                    row.createCell(5).setCellValue(scan.getScanTime() != null ? 
                            scan.getScanTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "");
                }
                current = current.plusDays(1);
            }
            
            for (int i = 0; i < collectionHeaders.length; i++) {
                collectionsSheet.autoSizeColumn(i);
            }
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
    
    private void createSummaryRow(Sheet sheet, int rowNum, String label) {
        createSummaryRow(sheet, rowNum, label, null, null);
    }
    
    private void createSummaryRow(Sheet sheet, int rowNum, String label, CellStyle style) {
        createSummaryRow(sheet, rowNum, label, null, style);
    }
    
    private void createSummaryRow(Sheet sheet, int rowNum, String label, Object value) {
        createSummaryRow(sheet, rowNum, label, value, null);
    }
    
    private void createSummaryRow(Sheet sheet, int rowNum, String label, Object value, CellStyle style) {
        Row row = sheet.createRow(rowNum);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        if (style != null) labelCell.setCellStyle(style);
        
        if (value != null) {
            Cell valueCell = row.createCell(1);
            if (value instanceof Number) {
                valueCell.setCellValue(((Number) value).doubleValue());
            } else {
                valueCell.setCellValue(value.toString());
            }
        }
    }
    
    // ============== EXISTING ENDPOINTS ==============
    
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
