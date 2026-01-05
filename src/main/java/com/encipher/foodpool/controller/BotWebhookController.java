package com.encipher.foodpool.controller;

import com.encipher.foodpool.model.*;
import com.encipher.foodpool.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * Controller for handling Zoho Cliq bot webhook requests.
 * This handles votes submitted through the bot interface.
 */
@RestController
@RequestMapping("/api/bot")
@RequiredArgsConstructor
@Slf4j
public class BotWebhookController {
    
    private final EmployeeService employeeService;
    private final FoodPoolService foodPoolService;
    private final CliqNotificationService cliqNotificationService;
    
    @Value("${app.cliq.bot-webhook-secret:}")
    private String webhookSecret;
    
    /**
     * Handle vote from bot button click
     * Expected payload: { "email": "user@email.com", "food": "veg|nonveg" }
     */
    @PostMapping("/vote")
    public ResponseEntity<?> handleBotVote(@RequestBody Map<String, Object> payload) {
        try {
            log.info("Bot vote received: {}", payload);
            
            // Extract user email from payload
            String email = extractEmail(payload);
            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "text", "Unable to identify user. Please vote through the web portal."
                ));
            }
            
            // Extract food choice
            String foodType = extractFoodType(payload);
            if (foodType == null || (!foodType.equals("veg") && !foodType.equals("nonveg"))) {
                return ResponseEntity.badRequest().body(Map.of(
                    "text", "Invalid food choice. Please select Veg or Non-Veg."
                ));
            }
            
            // Find employee
            Employee employee = employeeService.findByEmail(email).orElse(null);
            if (employee == null) {
                return ResponseEntity.ok(Map.of(
                    "text", "You are not registered in the system. Please contact admin."
                ));
            }
            
            // Check if pool is open
            if (!foodPoolService.isPoolOpen()) {
                return ResponseEntity.ok(Map.of(
                    "text", "The food survey is currently closed. Please wait for the next survey."
                ));
            }
            
            // Register vote
            FoodPool pool = foodPoolService.registerPool(employee, foodType);
            
            String message = String.format(
                "Vote recorded!\n\nName: %s\nChoice: %s\nFood Date: %s\n\nThank you for participating!",
                employee.getName(),
                foodType.toUpperCase(),
                pool.getFoodDate()
            );
            
            return ResponseEntity.ok(Map.of("text", message));
            
        } catch (Exception e) {
            log.error("Error processing bot vote: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "text", "Error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Handle slash commands from the bot
     * Commands: /foodpool status, /foodpool vote veg, /foodpool vote nonveg
     */
    @PostMapping("/command")
    public ResponseEntity<?> handleBotCommand(@RequestBody Map<String, Object> payload) {
        try {
            log.info("Bot command received: {}", payload);
            
            String email = extractEmail(payload);
            String command = extractCommand(payload);
            
            if (email == null || email.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "text", "Unable to identify user."
                ));
            }
            
            Employee employee = employeeService.findByEmail(email).orElse(null);
            if (employee == null) {
                return ResponseEntity.ok(Map.of(
                    "text", "You are not registered in the system."
                ));
            }
            
            // Parse command
            if (command == null || command.isEmpty() || command.equals("help")) {
                return ResponseEntity.ok(Map.of(
                    "text", getHelpMessage()
                ));
            }
            
            if (command.equals("status")) {
                return handleStatusCommand(employee);
            }
            
            if (command.startsWith("vote ")) {
                String foodType = command.substring(5).trim().toLowerCase();
                return handleVoteCommand(employee, foodType);
            }
            
            return ResponseEntity.ok(Map.of(
                "text", "Unknown command. Type 'help' for available commands."
            ));
            
        } catch (Exception e) {
            log.error("Error processing bot command: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "text", "Error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Webhook for button actions from Zoho Cliq
     */
    @PostMapping("/action")
    public ResponseEntity<?> handleButtonAction(@RequestBody Map<String, Object> payload) {
        try {
            log.info("Bot action received: {}", payload);
            
            // Extract action data
            @SuppressWarnings("unchecked")
            Map<String, Object> actionData = (Map<String, Object>) payload.get("data");
            if (actionData == null) {
                return ResponseEntity.ok(Map.of("text", "Invalid action data"));
            }
            
            String food = (String) actionData.get("food");
            String email = extractEmail(payload);
            
            if (food == null || email == null) {
                return ResponseEntity.ok(Map.of("text", "Missing required data"));
            }
            
            // Process the vote
            Employee employee = employeeService.findByEmail(email).orElse(null);
            if (employee == null) {
                return ResponseEntity.ok(Map.of("text", "User not found"));
            }
            
            if (!foodPoolService.isPoolOpen()) {
                return ResponseEntity.ok(Map.of("text", "Survey is closed"));
            }
            
            FoodPool pool = foodPoolService.registerPool(employee, food);
            
            return ResponseEntity.ok(Map.of(
                "text", String.format("Vote recorded: %s for %s", food.toUpperCase(), pool.getFoodDate())
            ));
            
        } catch (Exception e) {
            log.error("Error processing button action: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("text", "Error: " + e.getMessage()));
        }
    }
    
    // ============== HELPER METHODS ==============
    
    private String extractEmail(Map<String, Object> payload) {
        // Try different paths where email might be
        if (payload.containsKey("email")) {
            return (String) payload.get("email");
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) payload.get("user");
        if (user != null && user.containsKey("email")) {
            return (String) user.get("email");
        }
        
        // Zoho Cliq specific paths
        @SuppressWarnings("unchecked")
        Map<String, Object> access = (Map<String, Object>) payload.get("access");
        if (access != null && access.containsKey("email")) {
            return (String) access.get("email");
        }
        
        return null;
    }
    
    private String extractFoodType(Map<String, Object> payload) {
        if (payload.containsKey("food")) {
            return ((String) payload.get("food")).toLowerCase();
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        if (data != null && data.containsKey("food")) {
            return ((String) data.get("food")).toLowerCase();
        }
        
        return null;
    }
    
    private String extractCommand(Map<String, Object> payload) {
        if (payload.containsKey("command")) {
            return ((String) payload.get("command")).toLowerCase().trim();
        }
        
        if (payload.containsKey("text")) {
            return ((String) payload.get("text")).toLowerCase().trim();
        }
        
        return "";
    }
    
    private ResponseEntity<?> handleStatusCommand(Employee employee) {
        MenuConfig menu = foodPoolService.getTodayMenu();
        LocalDate foodDate = menu.getFoodDate() != null ? menu.getFoodDate() : LocalDate.now().plusDays(1);
        
        boolean poolOpen = menu.isPoolOpen();
        boolean hasVoted = foodPoolService.hasPooledForFoodDate(employee.getEmployeeId(), foodDate);
        
        String status;
        if (!poolOpen) {
            status = "Food survey is currently CLOSED.";
        } else if (hasVoted) {
            String choice = foodPoolService.getPoolForFoodDate(employee.getEmployeeId(), foodDate)
                    .map(FoodPool::getFoodType)
                    .map(String::toUpperCase)
                    .orElse("UNKNOWN");
            status = String.format("You have voted: %s\nFood Date: %s\n\nYou can change your choice while survey is open.", 
                    choice, foodDate);
        } else {
            status = String.format("Survey is OPEN!\nFood Date: %s\n\nYou haven't voted yet. Reply with:\n- vote veg\n- vote nonveg", 
                    foodDate);
        }
        
        return ResponseEntity.ok(Map.of("text", status));
    }
    
    private ResponseEntity<?> handleVoteCommand(Employee employee, String foodType) throws Exception {
        if (!foodType.equals("veg") && !foodType.equals("nonveg")) {
            return ResponseEntity.ok(Map.of(
                "text", "Invalid choice. Use: vote veg OR vote nonveg"
            ));
        }
        
        if (!foodPoolService.isPoolOpen()) {
            return ResponseEntity.ok(Map.of(
                "text", "Survey is currently closed."
            ));
        }
        
        FoodPool pool = foodPoolService.registerPool(employee, foodType);
        
        return ResponseEntity.ok(Map.of(
            "text", String.format("Vote recorded: %s\nFood Date: %s\n\nThank you!", 
                    foodType.toUpperCase(), pool.getFoodDate())
        ));
    }
    
    private String getHelpMessage() {
        return "Food Pool Bot Commands:\n\n" +
               "- status : Check your current status and survey info\n" +
               "- vote veg : Vote for vegetarian meal\n" +
               "- vote nonveg : Vote for non-vegetarian meal\n" +
               "- help : Show this help message";
    }
}

